"""JSONL dialogue loader and next-byte batches."""
import json
from pathlib import Path
import torch
from .tokenizer import BozerByteTokenizer


def _format_jsonl(path):
    lines = []
    for number, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        try:
            item = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSONL at {path}:{number}: {exc}") from exc
        if not isinstance(item, dict):
            raise ValueError(f"record at {path}:{number} must be an object")
        fields = [item.get(k) for k in ("category", "user", "assistant")]
        if not all(isinstance(x, str) and x.strip() for x in fields):
            raise ValueError(f"record at {path}:{number} needs category, user and assistant")
        lines.append(f"Utilisateur : {fields[1].strip()}\\nBOZER : {fields[2].strip()}\\n")
    if not lines:
        raise ValueError(f"corpus is empty: {path}")
    return "\\n".join(lines)


def load_corpus(path):
    path = Path(path)
    text = _format_jsonl(path) if path.suffix.lower() == ".jsonl" else path.read_text(encoding="utf-8")
    if not text.strip():
        raise ValueError("training corpus is empty")
    ids = BozerByteTokenizer().encode(text)
    if len(ids) < 4:
        raise ValueError("training corpus is too short")
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


def get_batch(tokens, batch_size, context_length, device="cpu"):
    if tokens.numel() <= context_length + 1:
        raise ValueError("corpus must contain more tokens than context_length + 1")
    starts = torch.randint(0, tokens.numel() - context_length - 1, (batch_size,))
    x = torch.stack([tokens[i:i + context_length] for i in starts])
    y = torch.stack([tokens[i + 1:i + context_length + 1] for i in starts])
    return x.to(device), y.to(device)
