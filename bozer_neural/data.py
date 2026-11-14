"""Dialogue loading, assistant-only targets and balanced language-model batches."""
import json
import unicodedata
from pathlib import Path
import torch
from .tokenizer import BOS, EOS, BozerByteTokenizer


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
        history = item.get("history", [])
        if not isinstance(history, list):
            raise ValueError(f"record at {path}:{number} history must be a list")
        clean_history = []
        for turn_index, turn in enumerate(history, 1):
            if not isinstance(turn, dict):
                raise ValueError(f"record at {path}:{number} history turn {turn_index} must be an object")
            for key in ("user", "assistant"):
                if not isinstance(turn.get(key), str) or not turn[key].strip():
                    raise ValueError(f"record at {path}:{number} history turn {turn_index} needs non-empty {key}")
            clean_history.append({"user": turn["user"].strip(), "assistant": turn["assistant"].strip()})
        records.append({
            **item,
            "category": item["category"].strip(),
            "user": item["user"].strip(),
            "assistant": item["assistant"].strip(),
            "history": clean_history,
        })
    if not records:
        raise ValueError(f"corpus is empty: {path}")
    return records


def ensure_disjoint_prompts(training_path, validation_path):
    """Refuse accidental prompt overlap between training and validation files."""
    if Path(training_path).resolve() == Path(validation_path).resolve():
        raise ValueError("training and validation paths must be different files")

    def normalize(value):
        return " ".join(unicodedata.normalize("NFKC", value).casefold().split())

    def collect_prompts(path):
        prompts = set()
        for row in read_records(path):
            prompts.update(normalize(turn["user"]) for turn in row["history"])
            prompts.add(normalize(row["user"]))
        return prompts

    training_prompts = collect_prompts(training_path)
    validation_prompts = collect_prompts(validation_path)
    overlap = training_prompts & validation_prompts
    if overlap:
        raise ValueError(
            f"training/validation leakage: {len(overlap)} normalized prompt(s) "
            "overlap across current or history turns"
        )
    return {"training_prompts": len(training_prompts),
            "validation_prompts": len(validation_prompts), "overlap": 0}

def format_prompt(item):
    turns = []
    for turn in item.get("history", []):
        turns.append(f"Utilisateur : {turn['user']}\nBOZER : {turn['assistant']}")
    turns.append(f"Utilisateur : {item['user']}\nBOZER : ")
    return "\n".join(turns)


def format_record(item):
    return format_prompt(item) + item["assistant"]


def read_corpus_text(path):
    """Text view used only to learn BPE merges from the training split."""
    path = Path(path)
    if path.suffix.lower() != ".jsonl":
        text = path.read_text(encoding="utf-8")
        if not text.strip():
            raise ValueError(f"corpus is empty: {path}")
        return text
    return "\n".join(format_record(item) for item in read_records(path))


def _encode_supervised_record(item, tokenizer):
    # Tokenize prompt and answer separately: same prompt encoding is used at inference.
    prompt_ids = tokenizer.encode(format_prompt(item), add_special_tokens=False)
    answer_ids = tokenizer.encode(item["assistant"], add_special_tokens=False)
    if not answer_ids:
        raise ValueError("assistant answer produced no tokens")
    tokens = [BOS] + prompt_ids + answer_ids + [EOS]
    # Mask is aligned to token positions; only answer tokens and EOS contribute to loss.
    loss_mask = [0] * (1 + len(prompt_ids)) + [1] * (len(answer_ids) + 1)
    return tokens, loss_mask


def load_supervised_corpus(path, tokenizer=None):
    """Load packed tokens and a mask that trains only BOZER's answer (plus EOS)."""
    path = Path(path)
    tokenizer = tokenizer or BozerByteTokenizer()
    all_tokens, all_masks = [], []
    if path.suffix.lower() == ".jsonl":
        for item in read_records(path):
            tokens, mask = _encode_supervised_record(item, tokenizer)
            all_tokens.extend(tokens)
            all_masks.extend(mask)
    else:
        text = path.read_text(encoding="utf-8")
        if not text.strip():
            raise ValueError(f"corpus is empty: {path}")
        tokens = tokenizer.encode(text)
        all_tokens.extend(tokens)
        all_masks.extend([0] + [1] * (len(tokens) - 1))
    if len(all_tokens) < 4:
        raise ValueError(f"training corpus is too short: {path}")
    return (
        torch.tensor(all_tokens, dtype=torch.long),
        torch.tensor(all_masks, dtype=torch.float32),
    )


def load_supervised_corpus_by_category(path, tokenizer=None):
    """Build category-isolated streams with assistant-only target masks."""
    path = Path(path)
    if path.suffix.lower() != ".jsonl":
        return None
    tokenizer = tokenizer or BozerByteTokenizer()
    buckets = {}
    for item in read_records(path):
        tokens, mask = _encode_supervised_record(item, tokenizer)
        bucket = buckets.setdefault(item["category"], {"tokens": [], "loss_mask": []})
        bucket["tokens"].extend(tokens)
        bucket["loss_mask"].extend(mask)
    return {
        category: {
            "tokens": torch.tensor(values["tokens"], dtype=torch.long),
            "loss_mask": torch.tensor(values["loss_mask"], dtype=torch.float32),
        }
        for category, values in sorted(buckets.items())
    }


def load_corpus(path, tokenizer=None):
    """Compatibility loader for unmasked text and tests."""
    tokens, _ = load_supervised_corpus(path, tokenizer)
    return tokens


def load_corpus_by_category(path, tokenizer=None):
    """Compatibility view returning only token streams by category."""
    grouped = load_supervised_corpus_by_category(path, tokenizer)
    if grouped is None:
        return None
    return {category: values["tokens"] for category, values in grouped.items()}


def _eligible_starts(loss_mask, context_length):
    if loss_mask.ndim != 1:
        raise ValueError("loss_mask must be a one-dimensional tensor")
    if loss_mask.numel() <= context_length:
        raise ValueError("corpus must contain at least context_length + 1 tokens")
    target_windows = loss_mask[1:].unfold(0, context_length, 1).sum(dim=1)
    eligible = (target_windows > 0).nonzero(as_tuple=True)[0]
    if eligible.numel() == 0:
        raise ValueError("no training window contains an assistant target")
    return eligible


def get_supervised_batch(tokens, loss_mask, batch_size, context_length,
                         device="cpu", generator=None):
    """Sample windows that contain answer targets and return the aligned target mask."""
    if tokens.ndim != 1 or loss_mask.ndim != 1 or tokens.numel() != loss_mask.numel():
        raise ValueError("tokens and loss_mask must be aligned one-dimensional tensors")
    if batch_size < 1 or context_length < 1:
        raise ValueError("batch_size and context_length must be positive")
    eligible = _eligible_starts(loss_mask, context_length)
    picks = torch.randint(0, eligible.numel(), (batch_size,), generator=generator)
    starts = eligible[picks]
    x = torch.stack([tokens[i:i + context_length] for i in starts])
    y = torch.stack([tokens[i + 1:i + context_length + 1] for i in starts])
    mask = torch.stack([loss_mask[i + 1:i + context_length + 1] for i in starts])
    return x.to(device), y.to(device), mask.to(device)


def get_balanced_supervised_batch(category_data, batch_size, context_length,
                                  device="cpu", generator=None):
    """Sample categories evenly; within each category, sample an answer-bearing window."""
    if not isinstance(category_data, dict) or not category_data:
        raise ValueError("category_data must be a non-empty category-to-stream mapping")
    if batch_size < 1 or context_length < 1:
        raise ValueError("batch_size and context_length must be positive")
    categories = sorted(category_data)
    for category in categories:
        values = category_data[category]
        tokens, mask = values["tokens"], values["loss_mask"]
        if tokens.ndim != 1 or mask.ndim != 1 or tokens.numel() != mask.numel():
            raise ValueError(f"category {category!r} has invalid token/mask streams")
        _eligible_starts(mask, context_length)
    offset = int(torch.randint(len(categories), (1,), generator=generator))
    category_indexes = (torch.arange(batch_size) + offset) % len(categories)
    category_indexes = category_indexes[torch.randperm(batch_size, generator=generator)]
    batches = []
    for index in category_indexes.tolist():
        values = category_data[categories[index]]
        batches.append(get_supervised_batch(
            values["tokens"], values["loss_mask"], 1, context_length,
            device=device, generator=generator
        ))
    return tuple(torch.cat([batch[position] for batch in batches], dim=0)
                 for position in range(3))



def get_balanced_batch(category_tokens, batch_size, context_length,
                       device="cpu", generator=None):
    """Compatibility sampler for unmasked token streams."""
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
    batches = [
        get_batch(
            category_tokens[categories[index]], 1, context_length,
            device=device, generator=generator
        )
        for index in category_indexes.tolist()
    ]
    return tuple(torch.cat([batch[position] for batch in batches], dim=0)
                 for position in range(2))


def split_corpus(tokens, validation_fraction=0.1, context_length=128):
    if not 0.0 < validation_fraction < 0.5:
        raise ValueError("validation_fraction must be between 0 and 0.5")
    split_at = int(tokens.numel() * (1.0 - validation_fraction))
    train_tokens, validation_tokens = tokens[:split_at], tokens[split_at:]
    minimum = context_length + 2
    if min(train_tokens.numel(), validation_tokens.numel()) < minimum:
        raise ValueError(f"corpus too small: each split needs at least {minimum} tokens")
    return train_tokens, validation_tokens


def get_batch(tokens, batch_size, context_length, device="cpu", generator=None):
    """Unmasked compatibility sampler; includes the final legal window."""
    if tokens.ndim != 1:
        raise ValueError("tokens must be a one-dimensional tensor")
    if batch_size < 1 or context_length < 1:
        raise ValueError("batch_size and context_length must be positive")
    start_count = tokens.numel() - context_length
    if start_count < 1:
        raise ValueError("corpus must contain at least context_length + 1 tokens")
    starts = torch.randint(0, start_count, (batch_size,), generator=generator)
    x = torch.stack([tokens[i:i + context_length] for i in starts])
    y = torch.stack([tokens[i + 1:i + context_length + 1] for i in starts])
    return x.to(device), y.to(device)
