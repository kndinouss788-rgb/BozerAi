"""BOZER NEURAL 1B decoder-only Transformer, initialized from random weights.

This module defines the target architecture; it does not contain trained weights.
The default model is very large. Use a small Bozer1BConfig for shape/unit tests.
"""
import math

import torch
from torch import nn
from torch.nn import functional as F

from .config_1b import Bozer1BConfig


class RMSNorm(nn.Module):
    """RMS normalization with one learned scale vector and no bias."""

    def __init__(self, width, eps=1e-6):
        super().__init__()
        self.weight = nn.Parameter(torch.ones(width))
        self.eps = eps

    def forward(self, x):
        input_dtype = x.dtype
        normalized = x.float() * torch.rsqrt(
            x.float().pow(2).mean(dim=-1, keepdim=True) + self.eps
        )
        return (normalized * self.weight.float()).to(input_dtype)


def apply_rope(x, theta=10_000.0, position_offset=0):
    """Apply rotary position embeddings to [batch, heads, sequence, head_dim]."""
    if x.ndim != 4:
        raise ValueError("RoPE input must have shape [batch, heads, sequence, head_dim]")
    head_dim = x.shape[-1]
    if head_dim % 2:
        raise ValueError("RoPE requires an even head dimension")
    length = x.shape[-2]
    device = x.device
    positions = torch.arange(
        position_offset, position_offset + length, device=device, dtype=torch.float32
    )
    frequencies = torch.arange(0, head_dim, 2, device=device, dtype=torch.float32)
    inverse_frequencies = torch.pow(
        torch.tensor(theta, device=device, dtype=torch.float32),
        -frequencies / head_dim,
    )
    angles = torch.outer(positions, inverse_frequencies)
    cosine = angles.cos()[None, None, :, :]
    sine = angles.sin()[None, None, :, :]

    pairs = x.float().reshape(*x.shape[:-1], head_dim // 2, 2)
    first, second = pairs[..., 0], pairs[..., 1]
    rotated = torch.stack(
        (first * cosine - second * sine, first * sine + second * cosine),
        dim=-1,
    ).flatten(-2)
    return rotated.to(dtype=x.dtype)


class GroupedQueryAttention(nn.Module):
    """Causal grouped-query attention with fewer key/value than query heads."""

    def __init__(self, config: Bozer1BConfig):
        super().__init__()
        self.n_query_heads = config.n_query_heads
        self.n_kv_heads = config.n_kv_heads
        self.head_dim = config.head_dim
        self.groups = config.gqa_groups
        self.dropout_p = config.dropout
        self.rope_theta = config.rope_theta

        self.q_proj = nn.Linear(
            config.d_model, config.n_query_heads * config.head_dim, bias=False
        )
        self.k_proj = nn.Linear(
            config.d_model, config.n_kv_heads * config.head_dim, bias=False
        )
        self.v_proj = nn.Linear(
            config.d_model, config.n_kv_heads * config.head_dim, bias=False
        )
        self.o_proj = nn.Linear(
            config.n_query_heads * config.head_dim, config.d_model, bias=False
        )

    def forward(self, x):
        batch, length, _ = x.shape
        q = self.q_proj(x).view(
            batch, length, self.n_query_heads, self.head_dim
        ).transpose(1, 2)
        k = self.k_proj(x).view(
            batch, length, self.n_kv_heads, self.head_dim
        ).transpose(1, 2)
        v = self.v_proj(x).view(
            batch, length, self.n_kv_heads, self.head_dim
        ).transpose(1, 2)

        q = apply_rope(q, self.rope_theta)
        k = apply_rope(k, self.rope_theta)

        # Expand each KV head across its query-head group. This keeps the
        # checkpoint layout faithful to GQA and works on older PyTorch builds.
        if self.groups > 1:
            k = k.repeat_interleave(self.groups, dim=1)
            v = v.repeat_interleave(self.groups, dim=1)

        if hasattr(F, "scaled_dot_product_attention"):
            attended = F.scaled_dot_product_attention(
                q,
                k,
                v,
                attn_mask=None,
                dropout_p=self.dropout_p if self.training else 0.0,
                is_causal=True,
            )
        else:
            scores = (q @ k.transpose(-2, -1)) * (self.head_dim ** -0.5)
            causal_mask = torch.triu(
                torch.ones(length, length, device=x.device, dtype=torch.bool),
                diagonal=1,
            )
            scores = scores.masked_fill(causal_mask, torch.finfo(scores.dtype).min)
            weights = torch.softmax(scores.float(), dim=-1).to(dtype=scores.dtype)
            if self.training and self.dropout_p:
                weights = F.dropout(weights, p=self.dropout_p)
            attended = weights @ v

        attended = attended.transpose(1, 2).contiguous().view(
            batch, length, self.n_query_heads * self.head_dim
        )
        return self.o_proj(attended)


class SwiGLU(nn.Module):
    """Bias-free gated MLP: down(silu(gate(x)) * up(x))."""

    def __init__(self, config: Bozer1BConfig):
        super().__init__()
        self.gate_proj = nn.Linear(config.d_model, config.ffn_dim, bias=False)
        self.up_proj = nn.Linear(config.d_model, config.ffn_dim, bias=False)
        self.down_proj = nn.Linear(config.ffn_dim, config.d_model, bias=False)
        self.dropout = nn.Dropout(config.dropout)

    def forward(self, x):
        gated = F.silu(self.gate_proj(x)) * self.up_proj(x)
        return self.dropout(self.down_proj(gated))


class Bozer1BBlock(nn.Module):
    """Pre-norm Transformer block with GQA and SwiGLU."""

    def __init__(self, config: Bozer1BConfig):
        super().__init__()
        self.attention_norm = RMSNorm(config.d_model, config.rms_norm_eps)
        self.attention = GroupedQueryAttention(config)
        self.mlp_norm = RMSNorm(config.d_model, config.rms_norm_eps)
        self.mlp = SwiGLU(config)

    def forward(self, x):
        x = x + self.attention(self.attention_norm(x))
        return x + self.mlp(self.mlp_norm(x))


class BozerNeural1B(nn.Module):
    """Random-init BOZER NEURAL 1B model; not a pretrained or trained checkpoint."""

    def __init__(self, config=None):
        super().__init__()
        self.config = config or Bozer1BConfig()
        self.token_embedding = nn.Embedding(
            self.config.vocab_size, self.config.d_model
        )
        self.embedding_dropout = nn.Dropout(self.config.dropout)
        self.blocks = nn.ModuleList(
            [Bozer1BBlock(self.config) for _ in range(self.config.n_layers)]
        )
        self.final_norm = RMSNorm(
            self.config.d_model, self.config.rms_norm_eps
        )
        self.apply(self._initialize_weights)

    @staticmethod
    def _initialize_weights(module):
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    def parameter_count(self):
        return sum(parameter.numel() for parameter in self.parameters())

    def forward(self, input_ids, targets=None, loss_mask=None):
        if input_ids.ndim != 2:
            raise ValueError("input_ids must have shape [batch, sequence]")
        if input_ids.dtype != torch.long:
            raise TypeError("input_ids must use torch.long token IDs")
        if input_ids.numel() and (
            int(input_ids.min()) < 0 or int(input_ids.max()) >= self.config.vocab_size
        ):
            raise ValueError("input_ids contain a token outside the model vocabulary")
        _, length = input_ids.shape
        if length < 1 or length > self.config.context_length:
            raise ValueError("sequence length must be between 1 and context_length")
        if targets is not None and targets.shape != input_ids.shape:
            raise ValueError("targets must have the same shape as input_ids")
        if loss_mask is not None:
            if targets is None:
                raise ValueError("loss_mask requires targets")
            if loss_mask.shape != targets.shape:
                raise ValueError("loss_mask must have the same shape as targets")

        x = self.embedding_dropout(self.token_embedding(input_ids))
        for block in self.blocks:
            x = block(x)
        hidden = self.final_norm(x)
        # The output projection is tied to the input token embeddings.
        logits = F.linear(hidden, self.token_embedding.weight)

        loss = None
        if targets is not None:
            if targets.dtype != torch.long:
                raise TypeError("targets must use torch.long token IDs")
            if loss_mask is None:
                loss = F.cross_entropy(
                    logits.reshape(-1, logits.size(-1)), targets.reshape(-1)
                )
            else:
                token_losses = F.cross_entropy(
                    logits.reshape(-1, logits.size(-1)),
                    targets.reshape(-1),
                    reduction="none",
                ).view_as(targets)
                weights = loss_mask.to(
                    device=token_losses.device, dtype=token_losses.dtype
                )
                denominator = weights.sum()
                if not bool(denominator > 0):
                    raise ValueError("loss_mask must include at least one target token")
                loss = (token_losses * weights).sum() / denominator
        return logits, loss

    @torch.no_grad()
    def generate(
        self,
        input_ids,
        max_new_tokens=80,
        temperature=0.8,
        top_k=40,
        eos_token_id=2,
        min_new_tokens=0,
        sampling_vocab_size=None,
    ):
        if input_ids.ndim != 2 or input_ids.shape[1] < 1:
            raise ValueError("input_ids must have shape [batch, non-empty sequence]")
        if max_new_tokens < 0 or not 0 <= min_new_tokens <= max_new_tokens:
            raise ValueError("generation token limits are invalid")
        if temperature <= 0:
            raise ValueError("temperature must be positive")
        if top_k is not None and top_k < 1:
            raise ValueError("top_k must be positive or None")
        if eos_token_id is not None and not 0 <= eos_token_id < self.config.vocab_size:
            raise ValueError("eos_token_id is outside the model vocabulary")
        if sampling_vocab_size is not None:
            if not 1 <= sampling_vocab_size <= self.config.vocab_size:
                raise ValueError("sampling_vocab_size is outside the model vocabulary")
            if eos_token_id is not None and eos_token_id >= sampling_vocab_size:
                raise ValueError("eos_token_id must be inside the active sampling vocabulary")

        self.eval()
        generated = input_ids
        finished = torch.zeros(
            generated.shape[0], dtype=torch.bool, device=generated.device
        )
        for step in range(max_new_tokens):
            context = generated[:, -self.config.context_length:]
            logits, _ = self(context)
            next_logits = logits[:, -1, :] / temperature
            if sampling_vocab_size is not None and sampling_vocab_size < self.config.vocab_size:
                next_logits[:, sampling_vocab_size:] = float("-inf")
            if eos_token_id is not None and step < min_new_tokens:
                next_logits[:, eos_token_id] = float("-inf")
            if top_k is not None and top_k < next_logits.size(-1):
                threshold = torch.topk(next_logits, top_k, dim=-1).values[:, -1, None]
                next_logits = next_logits.masked_fill(
                    next_logits < threshold, float("-inf")
                )
            probabilities = torch.softmax(next_logits.float(), dim=-1)
            next_token = torch.multinomial(probabilities, num_samples=1)
            if eos_token_id is not None:
                next_token = torch.where(
                    finished[:, None],
                    torch.full_like(next_token, eos_token_id),
                    next_token,
                )
                finished = finished | next_token[:, 0].eq(eos_token_id)
            generated = torch.cat((generated, next_token), dim=1)
            if eos_token_id is not None and bool(finished.all()):
                break
        return generated
