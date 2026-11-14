"""Held-out evaluation for BOZER NEURAL 1B checkpoints."""
import argparse
import json
from pathlib import Path

import torch

from .config_1b import Bozer1BConfig
from .data import (
    format_prompt,
    get_supervised_batch,
    load_supervised_corpus,
    load_supervised_corpus_by_category,
    read_records,
)
from .model_1b import BozerNeural1B
from .tokenizer import BozerByteBPETokenizer, EOS
from .train_1b import CHECKPOINT_FORMAT
from .validate_corpus import validate_splits


@torch.no_grad()
def evaluate_1b(
    checkpoint_path,
    train_data_path,
    validation_data_path,
    test_data_path,
    batches=12,
    batch_size=1,
    samples=12,
    max_new_tokens=96,
    seed=29,
    device="cpu",
):
    """Evaluate only after proving train/validation/test prompt separation."""
    if batches < 1 or batch_size < 1 or samples < 0 or max_new_tokens < 0:
        raise ValueError("batches/batch_size must be positive; samples and max_new_tokens non-negative")
    split_report = validate_splits(
        train_data_path, validation_data_path, test_data_path
    )
    target_device = torch.device(device)
    if target_device.type == "cuda" and not torch.cuda.is_available():
        raise ValueError("CUDA was requested but is not available")

    checkpoint = torch.load(
        checkpoint_path, map_location="cpu", weights_only=False
    )
    if checkpoint.get("format") != CHECKPOINT_FORMAT:
        raise ValueError("unsupported BOZER NEURAL 1B checkpoint format")
    config = Bozer1BConfig.from_dict(checkpoint["config"])
    tokenizer = BozerByteBPETokenizer.from_dict(checkpoint["tokenizer"])
    if tokenizer.vocab_size != config.vocab_size:
        raise ValueError("checkpoint tokenizer and model vocabularies do not match")

    model = BozerNeural1B(config).to(target_device)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    tokens, loss_mask = load_supervised_corpus(test_data_path, tokenizer)
    category_data = load_supervised_corpus_by_category(test_data_path, tokenizer)
    category_test_loss = {}
    if category_data:
        for index, (category, values) in enumerate(sorted(category_data.items())):
            generator = torch.Generator(device="cpu").manual_seed(seed + index)
            scores = []
            for _ in range(batches):
                x, y, mask = get_supervised_batch(
                    values["tokens"], values["loss_mask"], batch_size,
                    config.context_length, device=target_device,
                    generator=generator,
                )
                _, loss = model(x, y, loss_mask=mask)
                scores.append(float(loss.detach().float().cpu()))
            category_test_loss[category] = sum(scores) / len(scores)
        # Macro-average prevents long categories from hiding weak categories.
        test_loss = sum(category_test_loss.values()) / len(category_test_loss)
    else:
        generator = torch.Generator(device="cpu").manual_seed(seed)
        scores = []
        for _ in range(batches):
            x, y, mask = get_supervised_batch(
                tokens, loss_mask, batch_size, config.context_length,
                device=target_device, generator=generator,
            )
            _, loss = model(x, y, loss_mask=mask)
            scores.append(float(loss.detach().float().cpu()))
        test_loss = sum(scores) / len(scores)

    records = read_records(test_data_path)
    selected = []
    selected_categories = set()
    for item in records:
        if item["category"] not in selected_categories:
            selected.append(item)
            selected_categories.add(item["category"])
    for item in records:
        if item not in selected and len(selected) < samples:
            selected.append(item)
    selected = selected[:samples]

    examples = []
    for item in selected:
        prompt_ids = tokenizer.encode(format_prompt(item))[:-1]
        prefix = torch.tensor(
            [prompt_ids[-config.context_length:]], dtype=torch.long,
            device=target_device,
        )
        prefix_length = prefix.shape[1]
        generated = model.generate(
            prefix,
            max_new_tokens=max_new_tokens,
            temperature=0.8,
            top_k=40,
            eos_token_id=EOS,
            sampling_vocab_size=tokenizer.active_vocab_size,
        )[0].tolist()
        completion = generated[prefix_length:]
        if EOS in completion:
            completion = completion[:completion.index(EOS)]
        examples.append({
            "category": item["category"],
            "prompt": item["user"],
            "reference": item["assistant"],
            "generated": tokenizer.decode(completion),
        })

    return {
        "checkpoint": str(checkpoint_path),
        "test_loss": test_loss,
        "category_test_loss": category_test_loss,
        "test_tokens": int(tokens.numel()),
        "test_examples": split_report["test"],
        "parameter_count": model.parameter_count(),
        "tokenizer_vocab_size": tokenizer.vocab_size,
        "tokenizer_active_vocab_size": tokenizer.active_vocab_size,
        "training_objective": checkpoint.get("training", {}).get(
            "training_objective", "assistant_answer_tokens_and_eos_only"
        ),
        "examples": examples,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Evaluate BOZER NEURAL 1B on an untouched French test partition"
    )
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--train", required=True)
    parser.add_argument("--validation", required=True)
    parser.add_argument("--test", required=True)
    parser.add_argument("--batches", type=int, default=12)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--samples", type=int, default=36)
    parser.add_argument("--max-new-tokens", type=int, default=96)
    parser.add_argument("--seed", type=int, default=29)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--report", default=None)
    args = parser.parse_args()

    result = evaluate_1b(
        checkpoint_path=args.checkpoint,
        train_data_path=args.train,
        validation_data_path=args.validation,
        test_data_path=args.test,
        batches=args.batches,
        batch_size=args.batch_size,
        samples=args.samples,
        max_new_tokens=args.max_new_tokens,
        seed=args.seed,
        device=args.device,
    )
    if args.report:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(f"evaluation_report={report_path}")
    print(
        f"test_loss={result['test_loss']:.4f} "
        f"test_tokens={result['test_tokens']} "
        f"parameters={result['parameter_count']} "
        f"active_vocab={result['tokenizer_active_vocab_size']}/"
        f"{result['tokenizer_vocab_size']}"
    )
    for category, score in sorted(result["category_test_loss"].items()):
        print(f"category={category} test_loss={score:.4f}")
    for index, item in enumerate(result["examples"], 1):
        print(
            f"\n[{index}] {item['category']}\nUtilisateur : {item['prompt']}"
            f"\nRéférence : {item['reference']}\nBOZER généré : {item['generated']}"
        )


if __name__ == "__main__":
    main()
