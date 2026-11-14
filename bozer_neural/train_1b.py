"""Supervised training entry point for the BOZER NEURAL 1B architecture.

Training always starts from random weights unless an explicit BOZER 1B checkpoint
is supplied with --resume. The small prototype trainer remains separate.
"""
import argparse
from dataclasses import replace
import json
import math
from pathlib import Path

import torch

from .config_1b import Bozer1BConfig
from .data import (
    ensure_disjoint_prompts,
    get_balanced_supervised_batch,
    get_supervised_batch,
    load_supervised_corpus,
    load_supervised_corpus_by_category,
    read_corpus_text,
)
from .model_1b import BozerNeural1B
from .tokenizer import BozerByteBPETokenizer


CHECKPOINT_FORMAT = "bozer-neural-1b-v0.1"


def _sample_batch(source, batch_size, context_length, device, generator):
    if isinstance(source, dict):
        return get_balanced_supervised_batch(
            source, batch_size, context_length,
            device=device, generator=generator,
        )
    tokens, loss_mask = source
    return get_supervised_batch(
        tokens, loss_mask, batch_size, context_length,
        device=device, generator=generator,
    )


@torch.no_grad()
def estimate_loss(model, source, config, batches=8, batch_size=1,
                  device="cpu", seed=2026, autocast_dtype=None):
    """Estimate assistant-only loss on a fixed validation sampling sequence."""
    if batches < 1 or batch_size < 1:
        raise ValueError("batches and batch_size must be positive")
    was_training = model.training
    model.eval()
    generator = torch.Generator(device="cpu").manual_seed(seed)
    scores = []
    try:
        for _ in range(batches):
            x, y, mask = _sample_batch(
                source, batch_size, config.context_length, device, generator
            )
            use_amp = autocast_dtype is not None and torch.device(device).type in (
                "cpu", "cuda"
            )
            with torch.autocast(
                device_type=torch.device(device).type,
                dtype=autocast_dtype,
                enabled=use_amp,
            ):
                _, loss = model(x, y, loss_mask=mask)
            scores.append(float(loss.detach().float().cpu()))
    finally:
        model.train(was_training)
    return sum(scores) / len(scores)


def scheduled_learning_rate(step, total_steps, base_lr, warmup_steps=100,
                            min_lr_ratio=0.1):
    """Linear warmup followed by cosine decay to a non-zero LR floor."""
    if base_lr <= 0:
        raise ValueError("learning_rate must be positive")
    if total_steps < 1 or warmup_steps < 0:
        raise ValueError("total_steps must be positive and warmup_steps non-negative")
    if not 0.0 <= min_lr_ratio <= 1.0:
        raise ValueError("min_lr_ratio must be between 0 and 1")
    if warmup_steps and step < warmup_steps:
        return base_lr * (step + 1) / warmup_steps
    decay_steps = max(1, total_steps - warmup_steps)
    progress = min(1.0, max(0.0, (step - warmup_steps) / decay_steps))
    cosine = 0.5 * (1.0 + math.cos(math.pi * progress))
    return base_lr * (min_lr_ratio + (1.0 - min_lr_ratio) * cosine)


def _atomic_save(payload, destination):
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    torch.save(payload, temporary)
    temporary.replace(destination)


def _precision_dtype(precision, device):
    if precision == "fp32":
        return None
    if precision == "bf16":
        if device.type == "cuda" and not torch.cuda.is_bf16_supported():
            raise ValueError("bf16 was requested but this CUDA device does not support it")
        return torch.bfloat16
    if precision == "fp16":
        if device.type != "cuda":
            raise ValueError("fp16 training is supported only on CUDA in this trainer")
        return torch.float16
    if precision != "auto":
        raise ValueError("precision must be auto, fp32, bf16 or fp16")
    if device.type == "cuda" and torch.cuda.is_bf16_supported():
        return torch.bfloat16
    return None


def train_1b(
    data_path,
    validation_data_path,
    output_path,
    steps=1000,
    batch_size=1,
    learning_rate=1e-4,
    seed=19,
    eval_interval=100,
    eval_batches=8,
    tokenizer_vocab_size=16_384,
    resume_checkpoint=None,
    grad_accum_steps=16,
    warmup_steps=100,
    min_lr_ratio=0.1,
    early_stopping_patience=8,
    device=None,
    precision="auto",
    config=None,
):
    """Train BOZER 1B on train/validation JSONL files without test-set access."""
    if steps < 1 or batch_size < 1 or eval_interval < 1 or eval_batches < 1:
        raise ValueError("steps, batch_size, eval_interval and eval_batches must be positive")
    if grad_accum_steps < 1:
        raise ValueError("grad_accum_steps must be positive")
    if early_stopping_patience < 0:
        raise ValueError("early_stopping_patience cannot be negative")
    if validation_data_path is None:
        raise ValueError("a separate validation_data_path is required for 1B training")

    target_device = torch.device(
        device or ("cuda" if torch.cuda.is_available() else "cpu")
    )
    if target_device.type == "cuda" and not torch.cuda.is_available():
        raise ValueError("CUDA was requested but is not available")
    autocast_dtype = _precision_dtype(precision, target_device)
    use_scaler = target_device.type == "cuda" and autocast_dtype == torch.float16

    torch.manual_seed(seed)
    if target_device.type == "cuda":
        torch.cuda.manual_seed_all(seed)

    previous = None
    if resume_checkpoint:
        previous = torch.load(
            resume_checkpoint, map_location="cpu", weights_only=False
        )
        if previous.get("format") != CHECKPOINT_FORMAT:
            raise ValueError("unsupported BOZER 1B checkpoint format")
        tokenizer = BozerByteBPETokenizer.from_dict(previous["tokenizer"])
    else:
        tokenizer = BozerByteBPETokenizer.train(
            read_corpus_text(data_path),
            vocab_size=tokenizer_vocab_size,
            pad_to_vocab_size=tokenizer_vocab_size,
        )

    requested_config = config or Bozer1BConfig(vocab_size=tokenizer.vocab_size)
    if requested_config.vocab_size != tokenizer.vocab_size:
        requested_config = replace(
            requested_config, vocab_size=tokenizer.vocab_size
        )
    model_config = requested_config
    if previous and previous.get("config") != model_config.to_dict():
        raise ValueError("resume checkpoint architecture differs from requested config")

    train_tokens, train_mask = load_supervised_corpus(data_path, tokenizer)
    validation_tokens, validation_mask = load_supervised_corpus(
        validation_data_path, tokenizer
    )
    train_categories = load_supervised_corpus_by_category(data_path, tokenizer)
    validation_categories = load_supervised_corpus_by_category(
        validation_data_path, tokenizer
    )
    split_audit = ensure_disjoint_prompts(data_path, validation_data_path)

    if train_categories and validation_categories:
        if set(train_categories) != set(validation_categories):
            raise ValueError("training and validation category sets do not match")
        train_source = train_categories
        validation_source = validation_categories
    else:
        train_source = (train_tokens, train_mask)
        validation_source = (validation_tokens, validation_mask)

    minimum = model_config.context_length + 2
    if min(train_tokens.numel(), validation_tokens.numel()) < minimum:
        raise ValueError(
            "training and validation splits must each contain at least "
            f"{minimum} tokens"
        )
    # Fail before allocating the large model if a category has no answer target.
    _sample_batch(
        train_source, 1, model_config.context_length, "cpu",
        torch.Generator(device="cpu").manual_seed(seed),
    )
    _sample_batch(
        validation_source, 1, model_config.context_length, "cpu",
        torch.Generator(device="cpu").manual_seed(seed + 1),
    )

    model = BozerNeural1B(model_config).to(target_device)
    if previous:
        model.load_state_dict(previous["model_state_dict"])

    optimizer = torch.optim.AdamW(
        model.parameters(), lr=learning_rate, betas=(0.9, 0.95), weight_decay=0.1
    )
    if previous and previous.get("optimizer_state_dict"):
        optimizer.load_state_dict(previous["optimizer_state_dict"])

    scaler = torch.cuda.amp.GradScaler(enabled=use_scaler)
    training_state = previous.get("training", {}) if previous else {}
    global_step = int(training_state.get("global_step", 0))
    total_target_step = global_step + steps
    batch_generator = torch.Generator(device="cpu")
    saved_generator_state = training_state.get("batch_generator_state")
    if saved_generator_state is not None:
        batch_generator.set_state(saved_generator_state)
    else:
        batch_generator.manual_seed(seed + global_step)

    initial_validation_loss = estimate_loss(
        model, validation_source, model_config,
        batches=eval_batches, batch_size=batch_size,
        device=target_device, seed=seed + 100_000,
        autocast_dtype=autocast_dtype,
    )
    best_validation_loss = float(
        training_state.get("best_validation_loss", initial_validation_loss)
    )
    if not previous:
        best_validation_loss = initial_validation_loss
    no_improvement = 0
    history = []
    best_path = Path(output_path).with_name(
        Path(output_path).stem + ".best" + Path(output_path).suffix
    )

    for local_step in range(steps):
        step = global_step + local_step
        lr = scheduled_learning_rate(
            step, total_target_step, learning_rate,
            warmup_steps=warmup_steps, min_lr_ratio=min_lr_ratio,
        )
        for group in optimizer.param_groups:
            group["lr"] = lr

        model.train()
        optimizer.zero_grad(set_to_none=True)
        accumulated_loss = 0.0
        for _ in range(grad_accum_steps):
            x, y, mask = _sample_batch(
                train_source, batch_size, model_config.context_length,
                target_device, batch_generator,
            )
            with torch.autocast(
                device_type=target_device.type,
                dtype=autocast_dtype,
                enabled=autocast_dtype is not None,
            ):
                _, loss = model(x, y, loss_mask=mask)
                scaled_loss = loss / grad_accum_steps
            if use_scaler:
                scaler.scale(scaled_loss).backward()
            else:
                scaled_loss.backward()
            accumulated_loss += float(loss.detach().float().cpu())

        if use_scaler:
            scaler.unscale_(optimizer)
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        if use_scaler:
            scaler.step(optimizer)
            scaler.update()
        else:
            optimizer.step()

        completed_step = step + 1
        should_evaluate = (
            completed_step % eval_interval == 0 or local_step == steps - 1
        )
        if not should_evaluate:
            continue

        validation_loss = estimate_loss(
            model, validation_source, model_config,
            batches=eval_batches, batch_size=batch_size,
            device=target_device, seed=seed + 100_000 + completed_step,
            autocast_dtype=autocast_dtype,
        )
        record = {
            "step": completed_step,
            "learning_rate": lr,
            "train_loss": accumulated_loss / grad_accum_steps,
            "validation_loss": validation_loss,
        }
        history.append(record)
        improved = validation_loss < best_validation_loss
        if improved:
            best_validation_loss = validation_loss
            no_improvement = 0
        else:
            no_improvement += 1

        payload = {
            "format": CHECKPOINT_FORMAT,
            "config": model_config.to_dict(),
            "tokenizer": tokenizer.to_dict(),
            "model_state_dict": model.state_dict(),
            "optimizer_state_dict": optimizer.state_dict(),
            "training": {
                "global_step": completed_step,
                "seed": seed,
                "best_validation_loss": best_validation_loss,
                "latest_validation_loss": validation_loss,
                "history": history,
                "split_audit": split_audit,
                "train_tokens": int(train_tokens.numel()),
                "validation_tokens": int(validation_tokens.numel()),
                "training_objective": "assistant_answer_tokens_and_eos_only",
                "precision": precision,
                "batch_generator_state": batch_generator.get_state(),
            },
        }
        _atomic_save(payload, output_path)
        if improved:
            _atomic_save(payload, best_path)
        if (
            early_stopping_patience
            and no_improvement >= early_stopping_patience
        ):
            break

    return {
        "checkpoint": str(output_path),
        "best_checkpoint": str(best_path),
        "global_step": completed_step,
        "best_validation_loss": best_validation_loss,
        "history": history,
        "parameter_count": model.parameter_count(),
        "split_audit": split_audit,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Train the BOZER NEURAL 1B model from random initialization"
    )
    parser.add_argument("--data", required=True, help="training JSONL")
    parser.add_argument("--validation-data", required=True, help="held-out validation JSONL")
    parser.add_argument("--output", default="artifacts/bozer-neural-1b.pt")
    parser.add_argument("--steps", type=int, default=1000)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--learning-rate", type=float, default=1e-4)
    parser.add_argument("--seed", type=int, default=19)
    parser.add_argument("--eval-interval", type=int, default=100)
    parser.add_argument("--eval-batches", type=int, default=8)
    parser.add_argument("--tokenizer-vocab-size", type=int, default=16_384)
    parser.add_argument("--resume", default=None)
    parser.add_argument("--grad-accum-steps", type=int, default=16)
    parser.add_argument("--warmup-steps", type=int, default=100)
    parser.add_argument("--min-lr-ratio", type=float, default=0.1)
    parser.add_argument("--early-stopping-patience", type=int, default=8)
    parser.add_argument("--device", default=None)
    parser.add_argument(
        "--precision", choices=("auto", "fp32", "bf16", "fp16"), default="auto"
    )
    parser.add_argument(
        "--config-json", default=None,
        help="optional JSON config; omit to use the full 1.116B target",
    )
    args = parser.parse_args()
    config = None
    if args.config_json:
        config = Bozer1BConfig.from_dict(
            json.loads(Path(args.config_json).read_text(encoding="utf-8"))
        )
    result = train_1b(
        data_path=args.data,
        validation_data_path=args.validation_data,
        output_path=args.output,
        steps=args.steps,
        batch_size=args.batch_size,
        learning_rate=args.learning_rate,
        seed=args.seed,
        eval_interval=args.eval_interval,
        eval_batches=args.eval_batches,
        tokenizer_vocab_size=args.tokenizer_vocab_size,
        resume_checkpoint=args.resume,
        grad_accum_steps=args.grad_accum_steps,
        warmup_steps=args.warmup_steps,
        min_lr_ratio=args.min_lr_ratio,
        early_stopping_patience=args.early_stopping_patience,
        device=args.device,
        precision=args.precision,
        config=config,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
