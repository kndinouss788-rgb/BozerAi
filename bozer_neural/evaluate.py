"""Evaluate a saved BOZER checkpoint on assistant-only targets in an isolated test split."""
import argparse
import json
from pathlib import Path
import torch
from .config import BozerConfig
from .data import (
    get_supervised_batch,
    load_supervised_corpus,
    load_supervised_corpus_by_category,
    read_records,
)
from .model import BozerTransformer
from .tokenizer import BozerByteBPETokenizer, BozerByteTokenizer, EOS


@torch.no_grad()
def evaluate(checkpoint_path, data_path, batches=12, batch_size=4, samples=6,
             max_new_tokens=96, seed=29):
    if batches < 1 or batch_size < 1 or samples < 0 or max_new_tokens < 0:
        raise ValueError("batches and batch_size must be positive; samples/max_new_tokens cannot be negative")
    torch.manual_seed(seed)
    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    if checkpoint.get("format") not in (
        "bozer-neural-v0.1", "bozer-neural-v0.2",
        "bozer-neural-v0.3", "bozer-neural-v0.4",
    ):
        raise ValueError("unsupported checkpoint format")
    config = BozerConfig(**checkpoint["config"])
    model = BozerTransformer(config)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    tokenizer_payload = checkpoint.get("tokenizer")
    tokenizer = (
        BozerByteBPETokenizer.from_dict(tokenizer_payload)
        if tokenizer_payload else BozerByteTokenizer()
    )
    if tokenizer.vocab_size != config.vocab_size:
        raise ValueError("checkpoint tokenizer and model vocabulary do not match")

    tokens, loss_mask = load_supervised_corpus(data_path, tokenizer)
    category_data = load_supervised_corpus_by_category(data_path, tokenizer)
    category_test_loss = {}
    if category_data:
        for category, values in category_data.items():
            generator = torch.Generator(device="cpu").manual_seed(seed)
            scores = []
            for _ in range(batches):
                x, y, mask = get_supervised_batch(
                    values["tokens"], values["loss_mask"], batch_size,
                    config.context_length, generator=generator
                )
                _, loss = model(x, y, loss_mask=mask)
                scores.append(float(loss))
            category_test_loss[category] = sum(scores) / len(scores)
        # Macro-average makes every category count equally, regardless of text length.
        test_loss = sum(category_test_loss.values()) / len(category_test_loss)
    else:
        generator = torch.Generator(device="cpu").manual_seed(seed)
        scores = []
        for _ in range(batches):
            x, y, mask = get_supervised_batch(
                tokens, loss_mask, batch_size, config.context_length,
                generator=generator
            )
            _, loss = model(x, y, loss_mask=mask)
            scores.append(float(loss))
        test_loss = sum(scores) / len(scores)

    records = read_records(data_path)
    selected = []
    seen_categories = set()
    for item in records:
        if item["category"] not in seen_categories:
            selected.append(item)
            seen_categories.add(item["category"])
    for item in records:
        if item not in selected and len(selected) < samples:
            selected.append(item)
    selected = selected[:samples]

    outputs = []
    for item in selected:
        prompt = f"Utilisateur : {item['user'].strip()}\nBOZER : "
        prefix_ids = tokenizer.encode(prompt)[:-1]
        prefix = torch.tensor([prefix_ids], dtype=torch.long)
        prefix = prefix[:, -config.context_length:]
        prefix_len = prefix.shape[1]
        generated = model.generate(
            prefix, max_new_tokens=max_new_tokens, temperature=0.8, top_k=40
        )[0].tolist()
        completion = generated[prefix_len:]
        if EOS in completion:
            completion = completion[:completion.index(EOS)]
        outputs.append({
            "category": item["category"], "prompt": item["user"],
            "reference": item["assistant"], "generated": tokenizer.decode(completion),
        })
    return {
        "test_loss": test_loss,
        "category_test_loss": category_test_loss,
        "test_tokens": int(tokens.numel()),
        "evaluated_categories": sorted({item["category"] for item in outputs}),
        "tokenizer_vocab_size": tokenizer.vocab_size,
        "training_objective": checkpoint.get("training", {}).get(
            "training_objective", "legacy_full_sequence"
        ),
        "examples": outputs,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--data", default="data/bozer_neural_v0_4/test_fr.jsonl")
    parser.add_argument("--batches", type=int, default=12)
    parser.add_argument("--samples", type=int, default=36)
    parser.add_argument("--max-new-tokens", type=int, default=96)
    args = parser.parse_args()
    result = evaluate(
        args.checkpoint, args.data, batches=args.batches,
        samples=args.samples, max_new_tokens=args.max_new_tokens
    )
    print(
        f"test_loss={result['test_loss']:.4f} test_tokens={result['test_tokens']} "
        f"tokenizer_vocab_size={result['tokenizer_vocab_size']} "
        f"objective={result['training_objective']}"
    )
    for category, value in sorted(result["category_test_loss"].items()):
        print(f"category={category} test_loss={value:.4f}")
    for index, item in enumerate(result["examples"], 1):
        print(
            f"\n[{index}] {item['category']}\nUtilisateur : {item['prompt']}"
            f"\nRéférence : {item['reference']}\nBOZER généré : {item['generated']}"
        )


if __name__ == "__main__":
    main()
