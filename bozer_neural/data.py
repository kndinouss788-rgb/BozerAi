"""Corpus loader with a contiguous holdout set and next-byte batches."""
from pathlib import Path
import torch
from .tokenizer import BozerByteTokenizer


def load_corpus(path):
    text = Path(path).read_text(encoding="utf-8")
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
        raise ValueError(
            f"corpus too small: each split needs at least {minimum} tokens"
        )
    return train_tokens, validation_tokens


def get_batch(tokens, batch_size, context_length, device="cpu"):
    if tokens.numel() <= context_length + 1:
        raise ValueError("corpus must contain more tokens than context_length + 1")
    starts = torch.randint(0, tokens.numel() - context_length - 1, (batch_size,))
    x = torch.stack([tokens[i:i + context_length] for i in starts])
    y = torch.stack([tokens[i + 1:i + context_length + 1] for i in starts])
    return x.to(device), y.to(device)
