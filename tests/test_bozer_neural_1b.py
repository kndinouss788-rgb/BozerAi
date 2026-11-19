"""Unit tests for the BOZER NEURAL 1B architecture using only a tiny model."""
import pytest
import torch

from bozer_neural.config_1b import Bozer1BConfig
from bozer_neural.model_1b import BozerNeural1B, apply_rope
from bozer_neural.tokenizer import BozerByteBPETokenizer
from bozer_neural.generation import GenerationConfig, generate
from bozer_neural.quantization import (
    _dequantize_tensor,
    _quantize_tensor,
    export_quantized_checkpoint,
    load_quantized_state_dict,
)
from bozer_neural.train_1b import CHECKPOINT_FORMAT
from bozer_neural.runtime import BozerNeuralRuntime


def tiny_config():
    return Bozer1BConfig(
        vocab_size=300,
        context_length=16,
        d_model=32,
        n_layers=2,
        n_query_heads=4,
        n_kv_heads=2,
        head_dim=8,
        ffn_dim=64,
    )


def test_default_architecture_has_exact_target_parameter_count():
    config = Bozer1BConfig()
    assert config.kv_dim == 512
    assert config.gqa_groups == 4
    assert config.parameter_count() == 1_115_785_216


def test_default_model_storage_estimates_match_parameter_count():
    config = Bozer1BConfig()
    params = config.parameter_count()

    assert config.weight_storage_bytes(32) == params * 4
    assert config.weight_storage_bytes(16) == params * 2
    assert config.weight_storage_bytes(8) == params
    assert config.weight_storage_bytes(4) == (params + 1) // 2
    assert config.kv_cache_bytes() == 100_663_296
    estimates = config.inference_storage_estimates()
    assert estimates["weights_int4_raw_bytes"] == (params + 1) // 2


def test_storage_estimates_reject_invalid_precision_and_context():
    config = tiny_config()
    with pytest.raises(ValueError, match="bits_per_parameter"):
        config.weight_storage_bytes(3)
    with pytest.raises(ValueError, match="context_length"):
        config.kv_cache_bytes(context_length=17)


def test_invalid_grouped_query_head_configuration_is_rejected():
    with pytest.raises(ValueError, match="divisible"):
        Bozer1BConfig(
            vocab_size=300,
            context_length=16,
            d_model=30,
            n_layers=1,
            n_query_heads=5,
            n_kv_heads=2,
            head_dim=6,
            ffn_dim=48,
        )


def test_small_model_parameter_count_matches_allocated_parameters():
    model = BozerNeural1B(tiny_config())
    assert model.parameter_count() == tiny_config().parameter_count()


def test_forward_loss_and_backward_pass():
    torch.manual_seed(7)
    model = BozerNeural1B(tiny_config(), gradient_checkpointing=True)
    input_ids = torch.randint(0, 300, (2, 8), dtype=torch.long)
    targets = torch.randint(0, 300, (2, 8), dtype=torch.long)
    loss_mask = torch.zeros((2, 8))
    loss_mask[:, 3:] = 1

    logits, loss = model(input_ids, targets, loss_mask)
    assert logits.shape == (2, 8, 300)
    assert loss is not None and torch.isfinite(loss)
    loss.backward()
    assert model.token_embedding.weight.grad is not None
    assert model.blocks[0].attention.k_proj.weight.grad is not None


def test_causal_attention_does_not_change_earlier_logits_when_future_changes():
    torch.manual_seed(11)
    model = BozerNeural1B(tiny_config()).eval()
    first = torch.tensor([[10, 11, 12, 13, 14]], dtype=torch.long)
    second = first.clone()
    second[0, 4] = 29

    with torch.no_grad():
        logits_a, _ = model(first)
        logits_b, _ = model(second)

    torch.testing.assert_close(logits_a[:, :4], logits_b[:, :4])


def test_incremental_kv_cache_matches_full_causal_forward():
    torch.manual_seed(23)
    model = BozerNeural1B(tiny_config()).eval()
    tokens = torch.tensor([[1, 12, 18, 24, 31, 42]], dtype=torch.long)

    with torch.inference_mode():
        full_logits, _ = model(tokens)
        cached_logits, cache = model.forward_with_cache(tokens[:, :4])
        next_logits, cache = model.forward_with_cache(
            tokens[:, 4:5], past_key_values=cache
        )
        final_logits, cache = model.forward_with_cache(
            tokens[:, 5:6], past_key_values=cache
        )

    torch.testing.assert_close(cached_logits, full_logits[:, :4], rtol=1e-5, atol=1e-6)
    torch.testing.assert_close(next_logits, full_logits[:, 4:5], rtol=1e-5, atol=1e-6)
    torch.testing.assert_close(final_logits, full_logits[:, 5:6], rtol=1e-5, atol=1e-6)
    assert cache[0][0].shape == (1, 2, 6, 8)
    assert cache[0][1].shape == (1, 2, 6, 8)


def test_cached_generation_rolls_over_context_window_safely():
    torch.manual_seed(29)
    model = BozerNeural1B(tiny_config()).eval()
    prompt = torch.tensor([[1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]])

    generated = generate(
        model,
        prompt,
        GenerationConfig(
            max_new_tokens=4,
            temperature=0,
            top_k=None,
            top_p=1.0,
            repetition_penalty=1.0,
            active_vocab_size=300,
            eos_token_id=None,
        ),
    )

    assert generated.shape == (1, prompt.shape[1] + 4)
    assert generated.dtype == torch.long


def test_rope_preserves_tensor_shape_and_dtype():
    value = torch.randn(2, 4, 7, 8, dtype=torch.float32)
    result = apply_rope(value)
    assert result.shape == value.shape
    assert result.dtype == value.dtype
    assert torch.isfinite(result).all()


def test_padded_bpe_vocabulary_round_trips_without_changing_active_tokens():
    tokenizer = BozerByteBPETokenizer.train(
        "Bonjour BOZER. Bonjour, comment vas-tu ?",
        vocab_size=300,
        pad_to_vocab_size=300,
    )
    restored = BozerByteBPETokenizer.from_dict(tokenizer.to_dict())
    assert tokenizer.vocab_size == 300
    assert tokenizer.active_vocab_size < tokenizer.vocab_size
    assert restored.vocab_size == tokenizer.vocab_size
    assert restored.active_vocab_size == tokenizer.active_vocab_size
    encoded = tokenizer.encode("Bonjour BOZER !")
    assert restored.decode(encoded) == "Bonjour BOZER !"
    assert restored.decode([restored.active_vocab_size]) == ""


def test_generation_respects_requested_length():
    torch.manual_seed(13)
    model = BozerNeural1B(tiny_config())
    prompt = torch.tensor([[1, 8, 9]], dtype=torch.long)
    generated = model.generate(
        prompt, max_new_tokens=3, min_new_tokens=3, top_k=10, eos_token_id=None,
        sampling_vocab_size=12,
    )
    assert generated.shape == (1, 6)
    assert int(generated.max()) < 12



def test_reserved_vocabulary_rows_are_excluded_from_supervised_loss():
    model = BozerNeural1B(tiny_config())
    input_ids = torch.tensor([[1, 10, 11, 12]], dtype=torch.long)
    targets = torch.tensor([[10, 11, 12, 13]], dtype=torch.long)

    logits, loss = model(
        input_ids, targets, active_vocab_size=270
    )

    assert torch.isfinite(loss)
    assert torch.isneginf(logits[..., 270:]).all()


def test_active_vocabulary_rejects_reserved_input_tokens():
    model = BozerNeural1B(tiny_config())
    input_ids = torch.tensor([[1, 10, 299]], dtype=torch.long)

    with pytest.raises(ValueError, match="inactive token"):
        model(input_ids, active_vocab_size=270)

@pytest.mark.parametrize("bits", [4, 8])
def test_quantized_tensor_round_trip_is_bounded_and_preserves_shape(bits):
    torch.manual_seed(37)
    source = torch.randn(3, 5)
    record = _quantize_tensor(source, bits)
    restored = _dequantize_tensor(record)

    assert restored.shape == source.shape
    assert restored.dtype == torch.float32
    max_error = float((source - restored).abs().max())
    assert max_error <= float(source.abs().max()) / (2 ** (bits - 1) - 1) + 1e-6


def test_int4_quantization_packs_two_values_per_byte():
    source = torch.tensor([-1.0, -0.5, 0.0, 0.5, 1.0])
    record = _quantize_tensor(source, 4)
    assert record["payload"].numel() == 3
    assert record["numel"] == 5
    assert _dequantize_tensor(record).shape == source.shape


@pytest.mark.parametrize("bits", [4, 8])
def test_export_quantized_checkpoint_requires_trained_checkpoint(tmp_path, bits):
    model = BozerNeural1B(tiny_config())
    tokenizer = BozerByteBPETokenizer.train(
        "bonjour bozer assistant réponse",
        vocab_size=300,
        pad_to_vocab_size=300,
    )
    source_path = tmp_path / "trained.pt"
    output_path = tmp_path / f"bozer-int{bits}.pt"
    torch.save({
        "format": CHECKPOINT_FORMAT,
        "config": tiny_config().to_dict(),
        "tokenizer": tokenizer.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": {
            "global_step": 2,
            "training_objective": "assistant_answer_tokens_and_eos_only",
            "best_validation_loss": 2.5,
        },
    }, source_path)

    report = export_quantized_checkpoint(source_path, output_path, bits=bits)
    artifact = torch.load(output_path, map_location="cpu", weights_only=True)
    restored = load_quantized_state_dict(artifact)

    assert report["bits"] == bits
    assert report["tensor_count"] == len(model.state_dict())
    assert output_path.exists()
    assert set(restored) == set(model.state_dict())
    for name, original in model.state_dict().items():
        assert restored[name].shape == original.shape
        assert torch.isfinite(restored[name]).all()


def test_export_rejects_initial_untrained_checkpoint(tmp_path):
    model = BozerNeural1B(tiny_config())
    tokenizer = BozerByteBPETokenizer.train(
        "bonjour bozer assistant réponse",
        vocab_size=300,
        pad_to_vocab_size=300,
    )
    source_path = tmp_path / "initial.pt"
    torch.save({
        "format": CHECKPOINT_FORMAT,
        "config": tiny_config().to_dict(),
        "tokenizer": tokenizer.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": {"global_step": 0},
    }, source_path)

    with pytest.raises(ValueError, match="untrained"):
        export_quantized_checkpoint(source_path, tmp_path / "bad.pt", bits=8)

def test_runtime_loads_packed_int8_checkpoint(tmp_path):
    model = BozerNeural1B(tiny_config())
    tokenizer = BozerByteBPETokenizer.train(
        "bonjour bozer assistant réponse",
        vocab_size=300,
        pad_to_vocab_size=300,
    )
    source_path = tmp_path / "trained.pt"
    output_path = tmp_path / "bozer-int8.pt"
    torch.save({
        "format": CHECKPOINT_FORMAT,
        "config": tiny_config().to_dict(),
        "tokenizer": tokenizer.to_dict(),
        "model_state_dict": model.state_dict(),
        "training": {"global_step": 2},
    }, source_path)
    export_quantized_checkpoint(source_path, output_path, bits=8)

    runtime = BozerNeuralRuntime.from_quantized_checkpoint(output_path)
    assert runtime.model.config == tiny_config()
    assert runtime.tokenizer.vocab_size == 300
    for name, tensor in runtime.model.state_dict().items():
        assert torch.isfinite(tensor).all(), name

