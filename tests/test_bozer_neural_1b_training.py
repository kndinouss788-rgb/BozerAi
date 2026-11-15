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
    assert saved["training"]["training_objective"] == "assistant_answer_tokens_and_eos_only"



def test_one_step_plain_text_pretraining_uses_all_next_tokens(tmp_path):
    train_path = tmp_path / "pretrain.txt"
    validation_path = tmp_path / "validation.txt"
    checkpoint_path = tmp_path / "bozer-text-pretrain.pt"
    train_path.write_text(
        "Le soleil se lève au-dessus de la ville.\n"
        "Les habitants commencent leur journée.\n\n"
        "Un robot apprend à classer des objets.\n"
        "Il vérifie chaque résultat avec attention.\n",
        encoding="utf-8",
    )
    validation_path.write_text(
        "La pluie tombe doucement sur les champs.\n"
        "Les plantes reçoivent l'eau nécessaire.\n\n"
        "Un véhicule autonome observe la route.\n"
        "Il respecte les règles de circulation.\n",
        encoding="utf-8",
    )

    config = Bozer1BConfig(
        vocab_size=259, context_length=8, d_model=16, n_layers=1,
        n_query_heads=2, n_kv_heads=1, head_dim=8, ffn_dim=32,
    )
    result = train_1b(
        data_path=train_path,
        validation_data_path=validation_path,
        output_path=checkpoint_path,
        steps=1, batch_size=1, learning_rate=1e-3,
        eval_interval=1, eval_batches=1, tokenizer_vocab_size=270,
        grad_accum_steps=1, warmup_steps=0, early_stopping_patience=0,
        device="cpu", precision="fp32", config=config,
    )

    saved = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    assert result["training_objective"] == "next_token_prediction_all_text_tokens"
    assert saved["training"]["training_objective"] == "next_token_prediction_all_text_tokens"
    assert saved["training"]["split_audit"]["mode"] == "plain_text_blocks"
    assert result["global_step"] == 1



def test_switching_from_text_pretraining_to_dialogue_tuning_resets_stage_state(tmp_path):
    train_text = tmp_path / "pretrain.txt"
    validation_text = tmp_path / "pretrain-validation.txt"
    train_text.write_text(
        "Les oiseaux chantent dans les arbres.\n"
        "Le vent traverse doucement la vallée.\n",
        encoding="utf-8",
    )
    validation_text.write_text(
        "Les étoiles apparaissent dans le ciel.\n"
        "La nuit devient calme et silencieuse.\n",
        encoding="utf-8",
    )
    config = Bozer1BConfig(
        vocab_size=259, context_length=8, d_model=16, n_layers=1,
        n_query_heads=2, n_kv_heads=1, head_dim=8, ffn_dim=32,
    )
    pretrain_checkpoint = tmp_path / "pretrain.pt"
    train_1b(
        data_path=train_text, validation_data_path=validation_text,
        output_path=pretrain_checkpoint, steps=2, batch_size=1,
        eval_interval=1, eval_batches=1, tokenizer_vocab_size=270,
        grad_accum_steps=1, warmup_steps=0, early_stopping_patience=0,
        device="cpu", precision="fp32", config=config,
    )

    dialogue_train = tmp_path / "dialogue-train.jsonl"
    dialogue_validation = tmp_path / "dialogue-validation.jsonl"
    write_record(
        dialogue_train, "Bonjour BOZER, comment vas-tu ?",
        "Je suis prêt à t'aider. Que souhaites-tu faire ?",
    )
    write_record(
        dialogue_validation, "Peux-tu expliquer un raisonnement ?",
        "Je vais présenter les étapes et vérifier chacune d'elles.",
    )
    dialogue_checkpoint = tmp_path / "dialogue-tuned.pt"
    result = train_1b(
        data_path=dialogue_train,
        validation_data_path=dialogue_validation,
        output_path=dialogue_checkpoint, steps=1, batch_size=1,
        eval_interval=1, eval_batches=1, tokenizer_vocab_size=270,
        resume_checkpoint=pretrain_checkpoint, grad_accum_steps=1,
        warmup_steps=0, early_stopping_patience=0, device="cpu",
        precision="fp32", config=config,
    )

    saved = torch.load(dialogue_checkpoint, map_location="cpu", weights_only=False)
    assert result["global_step"] == 1
    assert saved["training"]["objective_changed_on_resume"] is True
    assert saved["training"]["previous_training_objective"] == "next_token_prediction_all_text_tokens"
    assert saved["training"]["training_objective"] == "assistant_answer_tokens_and_eos_only"
