"""Tests for the separate BOZER NEURAL 1B training entry point."""
import json

import torch

from bozer_neural.config_1b import Bozer1BConfig
from bozer_neural.train_1b import (
    minimum_training_memory_bytes,
    scheduled_learning_rate,
    train_1b,
)


def write_record(path, user, assistant):
    path.write_text(
        json.dumps({
            "category": "dialogue_naturel",
            "user": user,
            "assistant": assistant,
        }, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def test_one_billion_parameter_training_memory_floor():
    config = Bozer1BConfig()
    assert minimum_training_memory_bytes(config) == 17_852_563_456


def test_training_learning_rate_schedule():
    assert scheduled_learning_rate(0, 10, 1.0, warmup_steps=2) == 0.5
    assert scheduled_learning_rate(1, 10, 1.0, warmup_steps=2) == 1.0
    assert scheduled_learning_rate(10, 10, 1.0, warmup_steps=2) == 0.1


def test_one_step_training_creates_a_versioned_checkpoint(tmp_path):
    train_path = tmp_path / "train.jsonl"
    validation_path = tmp_path / "validation.jsonl"
    checkpoint_path = tmp_path / "bozer-small-1b-path-test.pt"
    write_record(
        train_path,
        "Bonjour, peux-tu m'aider avec un calcul simple ?",
        "Oui. Donne-moi le calcul et je vais le résoudre étape par étape.",
    )
    write_record(
        validation_path,
        "Comment vérifier si une réponse est correcte ?",
        "Je vais reprendre les étapes et vérifier le résultat.",
    )

    config = Bozer1BConfig(
        vocab_size=259,
        context_length=8,
        d_model=16,
        n_layers=1,
        n_query_heads=2,
        n_kv_heads=1,
        head_dim=8,
        ffn_dim=32,
    )
    result = train_1b(
        data_path=train_path,
        validation_data_path=validation_path,
        output_path=checkpoint_path,
        steps=1,
        batch_size=1,
        learning_rate=1e-3,
        eval_interval=1,
        eval_batches=1,
        tokenizer_vocab_size=270,
        grad_accum_steps=1,
        warmup_steps=0,
        early_stopping_patience=0,
        device="cpu",
        precision="fp32",
        config=config,
    )

    assert checkpoint_path.exists()
    assert (tmp_path / "bozer-small-1b-path-test.best.pt").exists()
    saved = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    assert saved["format"] == "bozer-neural-1b-v0.1"
    assert result["global_step"] == 1
    assert result["parameter_count"] == Bozer1BConfig.from_dict(
        saved["config"]
    ).parameter_count()
    assert saved["training"]["split_audit"]["overlap"] == 0
    assert saved["tokenizer"]["vocab_size"] == 270
    assert saved["tokenizer"]["active_vocab_size"] <= 270
    assert saved["config"]["vocab_size"] == 270
    assert saved["training"]["gradient_checkpointing"] is True
