"""Evaluate a saved checkpoint on a separate JSONL test set."""
import argparse
import json
from pathlib import Path
import torch
from .config import BozerConfig
from .data import get_batch, load_corpus
from .model import BozerTransformer
from .tokenizer import BozerByteTokenizer


@torch.no_grad()
def evaluate(checkpoint_path, data_path, batches=12, batch_size=4, samples=6,
             max_new_tokens=96, seed=29):
    torch.manual_seed(seed)
    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    if checkpoint.get("format") != "bozer-neural-v0.1":
        raise ValueError("unsupported checkpoint format")
    config = BozerConfig(**checkpoint["config"])
    model = BozerTransformer(config)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()
    tokens = load_corpus(data_path)
    losses = []
    for _ in range(batches):
        x, y = get_batch(tokens, batch_size, config.context_length)
        _, loss = model(x, y)
        losses.append(float(loss))
    records = [json.loads(x) for x in Path(data_path).read_text(encoding="utf-8").splitlines() if x.strip()]
    tokenizer = BozerByteTokenizer()
    outputs = []
    for item in records[:samples]:
        prompt = f"Utilisateur : {item['user'].strip()}\\nBOZER : "
        prefix = torch.tensor([tokenizer.encode(prompt)], dtype=torch.long)
        prefix = prefix[:, -config.context_length:]
        prefix_len = prefix.shape[1]
        generated = model.generate(prefix, max_new_tokens=max_new_tokens, temperature=0.8, top_k=40)[0].tolist()
        completion = generated[prefix_len:]
        if 2 in completion:
            completion = completion[:completion.index(2)]
        outputs.append({"category": item["category"], "prompt": item["user"],
                        "reference": item["assistant"], "generated": tokenizer.decode(completion)})
    return {"test_loss": sum(losses) / len(losses), "test_tokens": int(tokens.numel()), "examples": outputs}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--data", default="data/bozer_neural_v0_2/test_fr.jsonl")
    parser.add_argument("--batches", type=int, default=12)
    parser.add_argument("--samples", type=int, default=6)
    parser.add_argument("--max-new-tokens", type=int, default=96)
    args = parser.parse_args()
    result = evaluate(args.checkpoint, args.data, batches=args.batches,
                      samples=args.samples, max_new_tokens=args.max_new_tokens)
    print(f"test_loss={result['test_loss']:.4f} test_tokens={result['test_tokens']}")
    for i, item in enumerate(result["examples"], 1):
        print(f"\\n[{i}] {item['category']}\\nUtilisateur : {item['prompt']}"
              f"\\nRéférence : {item['reference']}\\nBOZER généré : {item['generated']}")


if __name__ == "__main__":
    main()
