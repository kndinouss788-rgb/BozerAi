"""Strict dialogue loading, per-example special tokens and language-model batches."""
import json
from pathlib import Path
import torch
from .tokenizer import BozerByteTokenizer


def read_records(path):
    """Read validated dialogue records from JSONL without losing example boundaries."""
    path = Path(path)
    records = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        try:
            item = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSONL at {path}:{number}: {exc}") from exc
        if not isinstance(item, dict):
            raise ValueError(f"record at {path}:{number} must be an object")
        for key in ("category", "user", "assistant"):
            if not isinstance(item.get(key), str) or not item[key].strip():
                raise ValueError(f"record at {path}:{number} needs non-empty {key}")
        records.append({
            **item,
            "category": item["category"].strip(),
            "user": item["user"].strip(),
            "assistant": item["assistant"].strip(),
        })
    if not records:
        raise ValueError(f"corpus is empty: {path}")
    return records


def format_record(item):
    """Use the same prompt/answer framing at training and inference time."""
    return f"Utilisateur : {item['user']}\nBOZER : {item['assistant']}"


def read_corpus_text(path):
    """Text view used only to learn BPE merges from the training split."""
    path = Path(path)
    if path.suffix.lower() != ".jsonl":
        text = path.read_text(encoding="utf-8")
        if not text.strip():
            raise ValueError(f"corpus is empty: {path}")
        return text
    return "\n".join(format_record(item) for item in read_records(path))


def load_corpus(path, tokenizer=None):
    """Encode every JSONL example independently as BOS ... EOS, then pack tokens."""
    path = Path(path)
    tokenizer = tokenizer or BozerByteTokenizer()
    if path.suffix.lower() != ".jsonl":
        text = path.read_text(encoding="utf-8")
        if not text.strip():
            raise ValueError(f"corpus is empty: {path}")
        ids = tokenizer.encode(text)
    else:
        ids = []
        for item in read_records(path):
            ids.extend(tokenizer.encode(format_record(item)))
    if len(ids) < 4:
        raise ValueError(f"training corpus is too short: {path}")
    return torch.tensor(ids, dtype=torch.long)


def split_corpus(tokens, validation_fraction=0.1, context_length=128):
    if not 0.0 < validation_fraction < 0.5:
        raise ValueError("validation_fraction must be between 0 and 0.5")
    split_at = int(tokens.numel() * (1.0 - validation_fraction))
    train_tokens, validation_tokens = tokens[:split_at], tokens[split_at:]
    minimum = context_length + 2
    if min(train_tokens.numel(), validation_tokens.numel()) < minimum:
        raise ValueError(f"corpus too small: each split needs at least {minimum} tokens")
    return train_tokens, validation_tokens



def load_corpus_by_category(path, tokenizer=None):
    """Build independent token streams so training can sample categories evenly."""
    path = Path(path)
    if path.suffix.lower() != ".jsonl":
        return None
    tokenizer = tokenizer or BozerByteTokenizer()
    buckets = {}
    for item in read_records(path):
        tokens = tokenizer.encode(format_record(item))
        buckets.setdefault(item["category"], []).extend(tokens)
    return {
        category: torch.tensor(tokens, dtype=torch.long)
        for category, tokens in sorted(buckets.items())
    }


def get_balanced_batch(category_tokens, batch_size, context_length,
                       device="cpu", generator=None):
    """Choose categories evenly across each batch, then sample a legal window."""
    if not isinstance(category_tokens, dict) or not category_tokens:
        raise ValueError("category_tokens must be a non-empty category-to-tensor mapping")
    if batch_size < 1 or context_length < 1:
        raise ValueError("batch_size and context_length must be positive")
    categories = sorted(category_tokens)
    for category in categories:
        stream = category_tokens[category]
        if stream.ndim != 1 or stream.numel() <= context_length:
            raise ValueError(
                f"category {category!r} needs at least {context_length + 1} tokens"
            )
    offset = int(torch.randint(len(categories), (1,), generator=generator))
    category_indexes = (torch.arange(batch_size) + offset) % len(categories)
    category_indexes = category_indexes[torch.randperm(batch_size, generator=generator)]
    inputs, targets = [], []
    for index in category_indexes.tolist():
        stream = category_tokens[categories[index]]
        start = int(torch.randint(
            0, stream.numel() - context_length, (1,), generator=generator
        ))
        inputs.append(stream[start:start + context_length])
        targets.append(stream[start + 1:start + context_length + 1])
    return torch.stack(inputs).to(device), torch.stack(targets).to(device)


def get_batch(tokens, batch_size, context_length, device="cpu", generator=None):
    if tokens.ndim != 1:
        raise ValueError("tokens must be a one-dimensional tensor")
    if batch_size < 1 or context_length < 1:
        raise ValueError("batch_size and context_length must be positive")
    # There are N-context_length legal starts. The previous upper bound
    # accidentally excluded the final legal window.
    start_count = tokens.numel() - context_length
    if start_count < 1:
        raise ValueError("corpus must contain at least context_length + 1 tokens")
    starts = torch.randint(0, start_count, (batch_size,), generator=generator)
    x = torch.stack([tokens[i:i + context_length] for i in starts])
    y = torch.stack([tokens[i + 1:i + context_length + 1] for i in starts])
    return x.to(device), y.to(device)
