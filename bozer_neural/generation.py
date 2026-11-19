"""Controlled autoregressive decoding for the BOZER NEURAL 1B model.

This reference decoder uses full-context recomputation. It is correct but does
not implement a KV cache; Android/production inference should use an optimized
runtime after checkpoint parity tests.
"""
from __future__ import annotations
from dataclasses import dataclass
import torch


@dataclass(frozen=True)
class GenerationConfig:
    max_new_tokens: int = 96
    min_new_tokens: int = 0
    temperature: float = 0.8
    top_k: int | None = 40
    top_p: float = 0.92
    repetition_penalty: float = 1.08
    repetition_window: int = 128
    no_repeat_ngram_size: int = 0
    eos_token_id: int | None = 2
    active_vocab_size: int | None = None
    seed: int | None = None

    def __post_init__(self):
        if self.max_new_tokens < 0:
            raise ValueError("max_new_tokens must be non-negative")
        if not 0 <= self.min_new_tokens <= self.max_new_tokens:
            raise ValueError("min_new_tokens must be within generation limits")
        if self.temperature < 0:
            raise ValueError("temperature must be non-negative")
        if self.top_k is not None and self.top_k < 1:
            raise ValueError("top_k must be positive or None")
        if not 0 < self.top_p <= 1:
            raise ValueError("top_p must be in (0, 1]")
        if self.repetition_penalty < 1:
            raise ValueError("repetition_penalty must be at least 1")
        if self.repetition_window < 0 or self.no_repeat_ngram_size < 0:
            raise ValueError("repetition limits must be non-negative")
        if self.active_vocab_size is not None and self.active_vocab_size < 3:
            raise ValueError("active_vocab_size must include special tokens")


def _ban_repeated_ngram(logits: torch.Tensor, history: list[int], n: int) -> None:
    if n <= 0 or len(history) < n - 1:
        return
    prefix = tuple(history[-(n - 1):]) if n > 1 else ()
    for index in range(len(history) - n + 1):
        existing = tuple(history[index:index + n])
        if n == 1 or existing[:-1] == prefix:
            token = existing[-1]
            if 0 <= token < logits.numel():
                logits[token] = float("-inf")


def filter_next_token_logits(
    logits: torch.Tensor,
    history: list[int],
    config: GenerationConfig,
    *,
    generated_count: int,
) -> torch.Tensor:
    """Apply vocabulary, repetition, EOS, top-k and top-p constraints."""
    if logits.ndim != 1:
        raise ValueError("logits must be a one-dimensional vocabulary vector")
    scores = logits.float().clone()
    vocab_size = scores.numel()
    if config.active_vocab_size is not None:
        if config.active_vocab_size > vocab_size:
            raise ValueError("active_vocab_size exceeds logits vocabulary")
        scores[config.active_vocab_size:] = float("-inf")
    if config.eos_token_id is not None:
        if not 0 <= config.eos_token_id < vocab_size:
            raise ValueError("eos_token_id is outside the logits vocabulary")
        if config.active_vocab_size is not None and config.eos_token_id >= config.active_vocab_size:
            raise ValueError("eos_token_id is outside the active vocabulary")
        if generated_count < config.min_new_tokens:
            scores[config.eos_token_id] = float("-inf")

    recent = history[-config.repetition_window:] if config.repetition_window else []
    if config.repetition_penalty > 1:
        for token in set(recent):
            if 0 <= token < vocab_size and torch.isfinite(scores[token]):
                scores[token] = (
                    scores[token] * config.repetition_penalty
                    if scores[token] < 0
                    else scores[token] / config.repetition_penalty
                )
    _ban_repeated_ngram(scores, history, config.no_repeat_ngram_size)

    if config.top_k is not None and config.top_k < vocab_size:
        values = torch.topk(scores, config.top_k).values
        scores[scores < values[-1]] = float("-inf")

    if config.top_p < 1:
        sorted_scores, sorted_indices = torch.sort(scores, descending=True)
        sorted_probs = torch.softmax(sorted_scores, dim=-1)
        cumulative = torch.cumsum(sorted_probs, dim=-1)
        remove = cumulative > config.top_p
        # Keep the first token that crosses the threshold.
        remove[1:] = remove[:-1].clone()
        remove[0] = False
        scores[sorted_indices[remove]] = float("-inf")

    if not torch.isfinite(scores).any():
        raise RuntimeError("generation constraints removed every possible token")
    return scores


def sample_next_token(
    logits: torch.Tensor,
    history: list[int],
    config: GenerationConfig,
    *,
    generated_count: int = 0,
    generator: torch.Generator | None = None,
) -> torch.Tensor:
    scores = filter_next_token_logits(
        logits, history, config, generated_count=generated_count
    )
    if config.temperature == 0:
        return torch.argmax(scores, dim=-1, keepdim=True)
    probabilities = torch.softmax(scores / config.temperature, dim=-1)
    if not torch.isfinite(probabilities).all() or probabilities.sum() <= 0:
        raise RuntimeError("invalid next-token probability distribution")
    return torch.multinomial(probabilities, num_samples=1, generator=generator)


@torch.inference_mode()
def generate(model, input_ids: torch.Tensor, config: GenerationConfig | None = None):
    """Generate tokens with stop-token handling and restore the model's mode."""
    config = config or GenerationConfig()
    if input_ids.ndim != 2 or input_ids.shape[1] < 1:
        raise ValueError("input_ids must have shape [batch, non-empty sequence]")
    if input_ids.dtype != torch.long:
        raise TypeError("input_ids must use torch.long token IDs")
    if input_ids.shape[1] > model.config.context_length:
        raise ValueError("prompt exceeds model context length")
    if config.active_vocab_size is not None and input_ids.numel():
        if int(input_ids.min()) < 0 or int(input_ids.max()) >= config.active_vocab_size:
            raise ValueError("prompt contains a token outside active vocabulary")

    was_training = model.training
    model.eval()
    generated = input_ids.clone()
    finished = torch.zeros(generated.shape[0], dtype=torch.bool, device=generated.device)
    generator = None
    if config.seed is not None:
        generator = torch.Generator(device=generated.device)
        generator.manual_seed(config.seed)

    try:
        cached_inference = callable(getattr(model, "forward_with_cache", None))
        past_key_values = None
        next_logits = None
        if cached_inference:
            prefill_logits, past_key_values = model.forward_with_cache(generated)
            next_logits = prefill_logits[:, -1, :]

        for step in range(config.max_new_tokens):
            if cached_inference:
                logits_for_step = next_logits
            else:
                context = generated[:, -model.config.context_length:]
                logits, _ = model(
                    context, active_vocab_size=config.active_vocab_size
                )
                logits_for_step = logits[:, -1, :]

            next_tokens = []
            for row in range(generated.shape[0]):
                if bool(finished[row]):
                    next_tokens.append(torch.tensor(
                        [config.eos_token_id if config.eos_token_id is not None else 0],
                        device=generated.device, dtype=torch.long
                    ))
                    continue
                history = generated[row].tolist()
                token = sample_next_token(
                    logits_for_step[row], history, config,
                    generated_count=step, generator=generator,
                )
                next_tokens.append(token.reshape(1))
            next_token_batch = torch.stack(next_tokens, dim=0)
            generated = torch.cat((generated, next_token_batch), dim=1)
            if config.eos_token_id is not None:
                finished |= next_token_batch[:, 0].eq(config.eos_token_id)
                if bool(finished.all()):
                    break

            if cached_inference and step + 1 < config.max_new_tokens:
                cached_length = past_key_values[0][0].shape[2]
                if cached_length + 1 >= model.config.context_length:
                    context = generated[:, -model.config.context_length:]
                    update_logits, past_key_values = model.forward_with_cache(context)
                else:
                    update_logits, past_key_values = model.forward_with_cache(
                        next_token_batch, past_key_values=past_key_values
                    )
                next_logits = update_logits[:, -1, :]

        return generated
    finally:
        model.train(was_training)
