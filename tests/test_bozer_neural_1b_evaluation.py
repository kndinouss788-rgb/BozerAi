"""Held-out evaluation tests for BOZER NEURAL 1B."""
import json

import torch

from bozer_neural.config_1b import Bozer1BConfig
from bozer_neural.data import read_corpus_text
from bozer_neural.evaluate_1b import evaluate_1b
from bozer_neural.model_1b import BozerNeural1B
from bozer_neural.tokenizer import BozerByteBPETokenizer


CATEGORIES = [
    "dialogue_naturel",
    "comprehension_demande",
    "raisonnement_resolution",
    "planification_missions",
    "utilisation_outils_verification",
    "securite_confidentialite_honnetete",
]


def write_split(path, split):
    rows = []
    for index, category in enumerate(CATEGORIES):
        rows.append({
            "category": category,
            "user": f"Question {split} numéro {index}: explique cette situation clairement.",
            "assistant": f"Pour le scénario {split} numéro {index}, je vérifie les éléments avant de répondre.",
            "scenario_id": f"{split}_scenario_{index}",
            "origin": "test_fixture",
        })
    path.write_text(
        "\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n",
        encoding="utf-8",
    )


def test_evaluator_checks_splits_and_reports_each_category(tmp_path):
    train_path = tmp_path / "train.jsonl"
    validation_path = tmp_path / "validation.jsonl"
    test_path = tmp_path / "test.jsonl"
    checkpoint_path = tmp_path / "bozer-1b-test.pt"
    write_split(train_path, "train")
    write_split(validation_path, "validation")
    write_split(test_path, "test")

    tokenizer = BozerByteBPETokenizer.train(
        read_corpus_text(train_path),
        vocab_size=270,
        pad_to_vocab_size=270,
    )
    config = Bozer1BConfig(
        vocab_size=tokenizer.vocab_size,
        context_length=8,
        d_model=16,
        n_layers=1,
        n_query_heads=2,
        n_kv_heads=1,
        head_dim=8,
        ffn_dim=32,
    )
    model = BozerNeural1B(config)
    torch.save({
        "format": "bozer-neural-1b-v0.1",
        "config": config.to_dict(),
        "tokenizer": tokenizer.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": {"training_objective": "assistant_answer_tokens_and_eos_only"},
    }, checkpoint_path)

    result = evaluate_1b(
        checkpoint_path,
        train_path,
        validation_path,
        test_path,
        batches=1,
        batch_size=1,
        samples=6,
        max_new_tokens=2,
        device="cpu",
    )
    assert result["test_tokens"] > 0
    assert len(result["category_test_loss"]) == 6
    assert len(result["examples"]) == 6
    assert result["tokenizer_vocab_size"] == 270
