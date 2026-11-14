"""Unit tests for the BOZER NEURAL 1B architecture using only a tiny model."""
import pytest
import torch

from bozer_neural.config_1b import Bozer1BConfig
from bozer_neural.model_1b import BozerNeural1B, apply_rope
from bozer_neural.tokenizer import BozerByteBPETokenizer


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
