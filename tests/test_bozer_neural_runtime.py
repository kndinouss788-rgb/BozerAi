"""Checkpoint-backed BOZER NEURAL runtime integration tests."""
import torch
import pytest

from bozer_neural.config_1b import Bozer1BConfig
from bozer_neural.model_1b import BozerNeural1B
from bozer_neural.runtime import BozerNeuralRuntime
from bozer_neural.train_1b import CHECKPOINT_FORMAT
from bozer_neural.tokenizer import BozerByteBPETokenizer
from bozer_neural.generation import GenerationConfig


def make_tiny_checkpoint(path):
    tokenizer = BozerByteBPETokenizer(padded_vocab_size=259)
    config = Bozer1BConfig(
        vocab_size=tokenizer.vocab_size,
        context_length=32,
        d_model=16,
        n_layers=1,
        n_query_heads=2,
        n_kv_heads=1,
        head_dim=8,
        ffn_dim=32,
    )
    model = BozerNeural1B(config)
    torch.save({
        "format": CHECKPOINT_FORMAT,
        "config": config.to_dict(),
        "tokenizer": tokenizer.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": {"training_objective": "assistant_answer_tokens_and_eos_only"},
    }, path)


def test_runtime_loads_checkpoint_and_generates_text(tmp_path):
    checkpoint = tmp_path / "tiny-bozer.pt"
    make_tiny_checkpoint(checkpoint)

    runtime = BozerNeuralRuntime.from_checkpoint(checkpoint, device="cpu")
    answer = runtime.complete(
        "Utilisateur : Bonjour\nBOZER : ",
        GenerationConfig(
            max_new_tokens=4,
            temperature=0,
            active_vocab_size=runtime.tokenizer.active_vocab_size,
            eos_token_id=2,
        ),
    )

    assert isinstance(answer, str)
    assert runtime.model.training is False


def test_runtime_rejects_checkpoint_with_incompatible_weights(tmp_path):
    checkpoint = tmp_path / "bad-bozer.pt"
    make_tiny_checkpoint(checkpoint)
    payload = torch.load(checkpoint, map_location="cpu", weights_only=True)
    payload["model_state_dict"] = {}
    torch.save(payload, checkpoint)

    with pytest.raises(RuntimeError):
        BozerNeuralRuntime.from_checkpoint(checkpoint, device="cpu")


def test_runtime_rejects_empty_prompt_after_checkpoint_load(tmp_path):
    checkpoint = tmp_path / "tiny-bozer.pt"
    make_tiny_checkpoint(checkpoint)
    runtime = BozerNeuralRuntime.from_checkpoint(checkpoint, device="cpu")

    with pytest.raises(ValueError, match="prompt must not be empty"):
        runtime.complete("   ")
