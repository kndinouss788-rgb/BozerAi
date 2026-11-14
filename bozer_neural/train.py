"""Train BOZER NEURAL V0.1 from random initialization; no pretrained weights."""
import argparse
import json
from pathlib import Path
import torch
from .config import BozerConfig
from .data import get_batch, load_corpus
from .model import BozerTransformer


def train(data_path, output_path, steps=300, batch_size=16, learning_rate=3e-4, seed=19):
    if steps < 1:
        raise ValueError("steps must be >= 1")
    torch.manual_seed(seed)
    config = BozerConfig()
    tokens = load_corpus(data_path)
    model = BozerTransformer(config)
    optimizer = torch.optim.AdamW(model.parameters(), lr=learning_rate)
    model.train()
    first_loss = None
    last_loss = None
    for step in range(steps):
        x, y = get_batch(tokens, batch_size, config.context_length)
        _, loss = model(x, y)
        if first_loss is None:
            first_loss = float(loss.detach())
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        last_loss = float(loss.detach())
        if (step + 1) % 50 == 0 or step == 0:
            print(f"step={step + 1}/{steps} loss={last_loss:.4f}")
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    torch.save({
        "format": "bozer-neural-v0.1",
        "config": config.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": {
            "steps": steps, "seed": seed, "first_loss": first_loss,
            "last_loss": last_loss, "corpus_tokens": int(tokens.numel())
        }
    }, destination)
    metadata = destination.with_suffix(".json")
    metadata.write_text(json.dumps({
        "format": "bozer-neural-v0.1", "config": config.to_dict(),
        "training": {"steps": steps, "seed": seed, "first_loss": first_loss,
                     "last_loss": last_loss, "corpus_tokens": int(tokens.numel())}
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    return first_loss, last_loss


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/bozer_seed_fr.txt")
    parser.add_argument("--output", default="artifacts/bozer-neural-v0.1.pt")
    parser.add_argument("--steps", type=int, default=300)
    parser.add_argument("--batch-size", type=int, default=16)
    args = parser.parse_args()
    train(args.data, args.output, args.steps, args.batch_size)


if __name__ == "__main__":
    main()
