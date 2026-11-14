"""Train BOZER NEURAL V0.1 from random initialization; no pretrained weights."""
import argparse
import json
from pathlib import Path
import torch
from .config import BozerConfig
from .data import get_batch, load_corpus, split_corpus
from .model import BozerTransformer


@torch.no_grad()
def estimate_loss(model, tokens, config, batches=8, batch_size=16):
    model.eval()
    losses = []
    for _ in range(batches):
        x, y = get_batch(tokens, batch_size, config.context_length)
        _, loss = model(x, y)
        losses.append(float(loss))
    model.train()
    return sum(losses) / len(losses)


def train(data_path, output_path, steps=300, batch_size=16,
          learning_rate=3e-4, seed=19, validation_fraction=0.1,
          eval_interval=50, eval_batches=8):
    if steps < 1 or eval_interval < 1 or eval_batches < 1:
        raise ValueError("steps, eval_interval and eval_batches must be >= 1")
    torch.manual_seed(seed)
    config = BozerConfig()
    all_tokens = load_corpus(data_path)
    train_tokens, validation_tokens = split_corpus(
        all_tokens, validation_fraction, config.context_length
    )
    model = BozerTransformer(config)
    optimizer = torch.optim.AdamW(model.parameters(), lr=learning_rate)
    model.train()
    first_train_loss = last_train_loss = None
    best_validation_loss = None
    history = []

    for step in range(steps):
        x, y = get_batch(train_tokens, batch_size, config.context_length)
        _, loss = model(x, y)
        if first_train_loss is None:
            first_train_loss = float(loss.detach())
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        last_train_loss = float(loss.detach())

        if step == 0 or (step + 1) % eval_interval == 0 or step + 1 == steps:
            train_eval = estimate_loss(
                model, train_tokens, config, eval_batches, batch_size
            )
            validation_eval = estimate_loss(
                model, validation_tokens, config, eval_batches, batch_size
            )
            best_validation_loss = (
                validation_eval if best_validation_loss is None
                else min(best_validation_loss, validation_eval)
            )
            history.append({
                "step": step + 1,
                "train_loss": train_eval,
                "validation_loss": validation_eval,
            })
            print(
                f"step={step + 1}/{steps} train_loss={train_eval:.4f} "
                f"validation_loss={validation_eval:.4f}"
            )

    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    metrics = {
        "steps": steps, "seed": seed,
        "first_train_loss": first_train_loss,
        "last_train_loss": last_train_loss,
        "best_validation_loss": best_validation_loss,
        "train_tokens": int(train_tokens.numel()),
        "validation_tokens": int(validation_tokens.numel()),
        "validation_fraction": validation_fraction,
        "eval_history": history,
    }
    payload = {
        "format": "bozer-neural-v0.1",
        "config": config.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": metrics,
    }
    torch.save(payload, destination)
    destination.with_suffix(".json").write_text(
        json.dumps({k: v for k, v in payload.items() if k != "model_state_dict"},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return first_train_loss, last_train_loss, best_validation_loss


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/bozer_seed_fr.txt")
    parser.add_argument("--output", default="artifacts/bozer-neural-v0.1.pt")
    parser.add_argument("--steps", type=int, default=300)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--validation-fraction", type=float, default=0.1)
    parser.add_argument("--eval-interval", type=int, default=50)
    args = parser.parse_args()
    train(args.data, args.output, args.steps, args.batch_size,
          validation_fraction=args.validation_fraction,
          eval_interval=args.eval_interval)


if __name__ == "__main__":
    main()
