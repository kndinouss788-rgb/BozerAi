"""Train or fine-tune BOZER NEURAL with assistant-only loss and balanced sampling."""
import argparse
import json
import math
from pathlib import Path
import torch
from .config import BozerConfig
from .data import (
    get_balanced_supervised_batch,
    get_supervised_batch,
    load_supervised_corpus,
    load_supervised_corpus_by_category,
    read_corpus_text,
    ensure_disjoint_prompts,
)
from .model import BozerTransformer
from .tokenizer import BozerByteBPETokenizer


@torch.no_grad()
def estimate_loss(model, source, config, batches=8, batch_size=16,
                  device="cpu", seed=2026):
    if batches < 1:
        raise ValueError("batches must be >= 1")
    was_training = model.training
    model.eval()
    generator = torch.Generator(device="cpu").manual_seed(seed)
    losses = []
    try:
        for _ in range(batches):
            if isinstance(source, dict):
                x, y, mask = get_balanced_supervised_batch(
                    source, batch_size, config.context_length,
                    device=device, generator=generator
                )
            else:
                tokens, loss_mask = source
                x, y, mask = get_supervised_batch(
                    tokens, loss_mask, batch_size, config.context_length,
                    device=device, generator=generator
                )
            _, loss = model(x, y, loss_mask=mask)
            losses.append(float(loss))
    finally:
        model.train(was_training)
    return sum(losses) / len(losses)


def estimate_category_losses(model, category_source, config, batches=2, batch_size=8,
                            device="cpu", seed=2026):
    """Measure validation loss independently for each category."""
    if not isinstance(category_source, dict):
        return {}
    return {
        category: estimate_loss(
            model, {category: values}, config, batches=batches,
            batch_size=batch_size, device=device, seed=seed + index
        )
        for index, (category, values) in enumerate(sorted(category_source.items()))
    }


def scheduled_learning_rate(step, total_steps, base_lr, warmup_steps=20,
                            min_lr_ratio=0.1):
    """Linear warmup followed by cosine decay to a non-zero LR floor."""
    if base_lr <= 0:
        raise ValueError("learning_rate must be positive")
    if not 0.0 <= min_lr_ratio <= 1.0:
        raise ValueError("min_lr_ratio must be between 0 and 1")
    if warmup_steps < 0 or total_steps < 1:
        raise ValueError("warmup_steps must be >= 0 and total_steps >= 1")
    if warmup_steps and step < warmup_steps:
        return base_lr * float(step + 1) / float(warmup_steps)
    decay_steps = max(1, total_steps - warmup_steps)
    progress = min(1.0, max(0.0, (step - warmup_steps) / decay_steps))
    cosine = 0.5 * (1.0 + math.cos(math.pi * progress))
    return base_lr * (min_lr_ratio + (1.0 - min_lr_ratio) * cosine)


def _atomic_torch_save(payload, destination):
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    torch.save(payload, temporary)
    temporary.replace(destination)


def _make_payload(model, config, tokenizer, optimizer, metrics,
                  include_optimizer=True):
    payload = {
        "format": "bozer-neural-v0.4",
        "config": config.to_dict(),
        "tokenizer": tokenizer.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": metrics,
    }
    if include_optimizer and optimizer is not None:
        payload["optimizer_state_dict"] = optimizer.state_dict()
    return payload


def train(data_path, output_path, steps=600, batch_size=8,
          learning_rate=3e-4, seed=19, validation_fraction=0.1,
          eval_interval=50, eval_batches=8, validation_data_path=None,
          tokenizer_vocab_size=512, resume_checkpoint=None,
          grad_accum_steps=2, warmup_steps=20, min_lr_ratio=0.1,
          early_stopping_patience=5, device=None):
    if steps < 1 or batch_size < 1 or eval_interval < 1 or eval_batches < 1:
        raise ValueError("steps, batch_size, eval_interval and eval_batches must be positive")
    if grad_accum_steps < 1:
        raise ValueError("grad_accum_steps must be positive")
    if early_stopping_patience < 0:
        raise ValueError("early_stopping_patience cannot be negative")
    if learning_rate <= 0:
        raise ValueError("learning_rate must be positive")
    if not 0.0 <= min_lr_ratio <= 1.0:
        raise ValueError("min_lr_ratio must be between 0 and 1")
    if not 0.0 < validation_fraction < 0.5:
        raise ValueError("validation_fraction must be between 0 and 0.5")

    chosen_device = device or ("cuda" if torch.cuda.is_available() else "cpu")
    target_device = torch.device(chosen_device)
    if target_device.type == "cuda" and not torch.cuda.is_available():
        raise ValueError("CUDA was requested but is not available")
    torch.manual_seed(seed)

    # Learn BPE only from train data once; fine-tuning reuses that exact vocabulary.
    previous = None
    if resume_checkpoint:
        previous = torch.load(resume_checkpoint, map_location="cpu", weights_only=False)
        if previous.get("format") not in (
            "bozer-neural-v0.1", "bozer-neural-v0.2",
            "bozer-neural-v0.3", "bozer-neural-v0.4",
        ):
            raise ValueError("unsupported resume checkpoint format")
        if not previous.get("tokenizer"):
            raise ValueError("resume checkpoint has no learned tokenizer")
        tokenizer = BozerByteBPETokenizer.from_dict(previous["tokenizer"])
    else:
        tokenizer = BozerByteBPETokenizer.train(
            read_corpus_text(data_path), vocab_size=tokenizer_vocab_size
        )

    config = BozerConfig(vocab_size=tokenizer.vocab_size)
    if previous and previous.get("config") != config.to_dict():
        raise ValueError("resume checkpoint architecture differs from current configuration")

    train_tokens, train_loss_mask = load_supervised_corpus(data_path, tokenizer)
    train_category_data = load_supervised_corpus_by_category(data_path, tokenizer)
    split_audit = None
    if validation_data_path:
        split_audit = ensure_disjoint_prompts(data_path, validation_data_path)
        validation_tokens, validation_loss_mask = load_supervised_corpus(
            validation_data_path, tokenizer
        )
        validation_category_data = load_supervised_corpus_by_category(
            validation_data_path, tokenizer
        )
        split_mode = "separate_file"
        if train_category_data and validation_category_data:
            if set(train_category_data) != set(validation_category_data):
                raise ValueError("training and validation category sets do not match")
    else:
        split_at = int(train_tokens.numel() * (1.0 - validation_fraction))
        all_loss_mask = train_loss_mask
        train_tokens, validation_tokens = train_tokens[:split_at], train_tokens[split_at:]
        train_loss_mask = all_loss_mask[:split_at]
        validation_loss_mask = all_loss_mask[split_at:]
        train_category_data = None
        validation_category_data = None
        split_mode = "contiguous_holdout"

    train_source = train_category_data or (train_tokens, train_loss_mask)
    validation_source = validation_category_data or (
        validation_tokens, validation_loss_mask
    )
    minimum_tokens = config.context_length + 2
    if train_tokens.numel() < minimum_tokens or validation_tokens.numel() < minimum_tokens:
        raise ValueError(
            "corpus too short after tokenization: "
            f"train={train_tokens.numel()}, validation={validation_tokens.numel()}; "
            f"each split needs at least {minimum_tokens} tokens"
        )
    if isinstance(train_source, tuple):
        # Fail early if a split contains no answer tokens in any sampled window.
        get_supervised_batch(
            train_tokens, train_loss_mask, 1, config.context_length
        )
        get_supervised_batch(
            validation_tokens, validation_loss_mask, 1, config.context_length
        )

    model = BozerTransformer(config).to(target_device)
    if previous:
        model.load_state_dict(previous["model_state_dict"])
    optimizer = torch.optim.AdamW(
        model.parameters(), lr=learning_rate, betas=(0.9, 0.95), weight_decay=0.1
    )
    if previous and previous.get("optimizer_state_dict"):
        optimizer.load_state_dict(previous["optimizer_state_dict"])

    previous_training = previous.get("training", {}) if previous else {}
    global_step = int(previous_training.get(
        "global_step", previous_training.get("steps", 0)
    ))
    total_target_step = global_step + steps
    train_generator = torch.Generator(device="cpu")
    saved_rng = previous_training.get("train_generator_state") if previous else None
    if saved_rng is not None:
        train_generator.set_state(saved_rng)
    else:
        train_generator.manual_seed(seed + global_step)

    initial_val = estimate_loss(
        model, validation_source, config, eval_batches, batch_size,
        target_device, seed=seed + 100000
    )
    best_validation_loss = initial_val
    best_path = Path(output_path).with_name(
        Path(output_path).stem + ".best" + Path(output_path).suffix
    )
    history = []
    first_train_loss = None
    last_train_loss = None
    no_improvement = 0
    current_step = global_step

    metrics = {
        "steps": 0, "global_step": current_step, "seed": seed,
        "best_validation_loss": best_validation_loss,
        "split_mode": split_mode, "tokenizer_vocab_size": tokenizer.vocab_size,
        "tokenizer_merges": len(tokenizer.merges),
        "resumed_from": str(resume_checkpoint) if resume_checkpoint else None,
        "train_tokens": int(train_tokens.numel()),
        "validation_tokens": int(validation_tokens.numel()),
        "training_objective": "assistant_answer_tokens_and_eos_only",
        "split_audit": split_audit,
        "eval_history": history,
    }
    _atomic_torch_save(
        _make_payload(model, config, tokenizer, None, metrics, False), best_path
    )

    model.train()
    for local_step in range(steps):
        lr = scheduled_learning_rate(
            current_step, total_target_step, learning_rate,
            warmup_steps=warmup_steps, min_lr_ratio=min_lr_ratio
        )
        for group in optimizer.param_groups:
            group["lr"] = lr
        optimizer.zero_grad(set_to_none=True)
        micro_losses = []
        for _ in range(grad_accum_steps):
            if isinstance(train_source, dict):
                x, y, mask = get_balanced_supervised_batch(
                    train_source, batch_size, config.context_length,
                    device=target_device, generator=train_generator
                )
            else:
                tokens, loss_mask = train_source
                x, y, mask = get_supervised_batch(
                    tokens, loss_mask, batch_size, config.context_length,
                    device=target_device, generator=train_generator
                )
            _, loss = model(x, y, loss_mask=mask)
            micro_losses.append(float(loss.detach()))
            (loss / grad_accum_steps).backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        current_step += 1
        average_loss = sum(micro_losses) / len(micro_losses)
        if first_train_loss is None:
            first_train_loss = average_loss
        last_train_loss = average_loss

        should_evaluate = (
            local_step == 0 or current_step % eval_interval == 0
            or local_step + 1 == steps
        )
        if not should_evaluate:
            continue

        train_eval = estimate_loss(
            model, train_source, config, eval_batches, batch_size,
            target_device, seed=seed + current_step
        )
        val_eval = estimate_loss(
            model, validation_source, config, eval_batches, batch_size,
            target_device, seed=seed + 100000
        )
        category_val_eval = estimate_category_losses(
            model, validation_category_data, config, batches=min(2, eval_batches),
            batch_size=batch_size, device=target_device, seed=seed + 200000
        )
        improved = val_eval < best_validation_loss
        if improved:
            best_validation_loss = val_eval
            no_improvement = 0
        else:
            no_improvement += 1

        history.append({
            "global_step": current_step, "learning_rate": lr,
            "train_loss": train_eval, "validation_loss": val_eval,
            "improved": improved,
        })
        print(
            f"step={current_step}/{total_target_step} lr={lr:.7g} "
            f"train_loss={train_eval:.4f} validation_loss={val_eval:.4f}"
        )

        metrics = {
            "steps": current_step - global_step,
            "global_step": current_step, "seed": seed,
            "first_train_loss": first_train_loss, "last_train_loss": last_train_loss,
            "best_validation_loss": best_validation_loss,
            "current_validation_loss": val_eval,
            "category_validation_loss": category_val_eval,
            "train_tokens": int(train_tokens.numel()),
            "validation_tokens": int(validation_tokens.numel()),
            "split_mode": split_mode,
            "tokenizer_vocab_size": tokenizer.vocab_size,
            "tokenizer_merges": len(tokenizer.merges),
            "resumed_from": str(resume_checkpoint) if resume_checkpoint else None,
            "validation_fraction": validation_fraction if split_mode == "contiguous_holdout" else None,
            "grad_accum_steps": grad_accum_steps, "warmup_steps": warmup_steps,
            "min_lr_ratio": min_lr_ratio, "device": str(target_device),
            "best_checkpoint": str(best_path),
            "training_objective": "assistant_answer_tokens_and_eos_only",
            "split_audit": split_audit,
            "train_generator_state": train_generator.get_state(),
            "eval_history": history,
        }
        if improved:
            _atomic_torch_save(
                _make_payload(model, config, tokenizer, None, metrics, False),
                best_path
            )

        latest_payload = _make_payload(
            model, config, tokenizer, optimizer, metrics, include_optimizer=True
        )
        _atomic_torch_save(latest_payload, output_path)
        if early_stopping_patience and no_improvement >= early_stopping_patience:
            print(f"early_stopping at step={current_step}")
            break

    metrics["steps"] = current_step - global_step
    metrics["global_step"] = current_step
    metrics["first_train_loss"] = first_train_loss
    metrics["last_train_loss"] = last_train_loss
    metrics["best_validation_loss"] = best_validation_loss
    metrics["train_generator_state"] = train_generator.get_state()
    final_payload = _make_payload(model, config, tokenizer, optimizer, metrics)
    _atomic_torch_save(final_payload, output_path)
    metadata = {
        key: value for key, value in final_payload.items()
        if key not in ("model_state_dict", "optimizer_state_dict")
    }
    metadata["training"].pop("train_generator_state", None)
    metadata_path = Path(output_path).with_suffix(".json")
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return first_train_loss, last_train_loss, best_validation_loss


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/bozer_neural_v0_4/train_fr.jsonl")
    parser.add_argument("--validation-data", default="data/bozer_neural_v0_4/validation_fr.jsonl")
    parser.add_argument("--output", default="artifacts/bozer-neural-v0.4.pt")
    parser.add_argument("--steps", type=int, default=600,
                        help="number of additional optimizer steps")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=3e-4)
    parser.add_argument("--validation-fraction", type=float, default=0.1)
    parser.add_argument("--eval-interval", type=int, default=50)
    parser.add_argument("--eval-batches", type=int, default=8)
    parser.add_argument("--tokenizer-vocab-size", type=int, default=512)
    parser.add_argument("--grad-accum-steps", type=int, default=2)
    parser.add_argument("--warmup-steps", type=int, default=20)
    parser.add_argument("--min-lr-ratio", type=float, default=0.1)
    parser.add_argument("--early-stopping-patience", type=int, default=5)
    parser.add_argument("--device", default=None)
    parser.add_argument("--resume", default=None,
                        help="fine-tune from a saved BOZER checkpoint")
    args = parser.parse_args()
    train(
        args.data, args.output, steps=args.steps, batch_size=args.batch_size,
        learning_rate=args.learning_rate, validation_fraction=args.validation_fraction,
        eval_interval=args.eval_interval, eval_batches=args.eval_batches,
        validation_data_path=args.validation_data,
        tokenizer_vocab_size=args.tokenizer_vocab_size,
        resume_checkpoint=args.resume, grad_accum_steps=args.grad_accum_steps,
        warmup_steps=args.warmup_steps, min_lr_ratio=args.min_lr_ratio,
        early_stopping_patience=args.early_stopping_patience, device=args.device,
    )


if __name__ == "__main__":
    main()
