"""Decoder-only Transformer implemented from randomly initialized PyTorch weights."""
import torch
from torch import nn
from .config import BozerConfig


class CausalSelfAttention(nn.Module):
    def __init__(self, config: BozerConfig):
        super().__init__()
        self.heads = config.n_heads
        self.head_dim = config.d_model // config.n_heads
        self.qkv = nn.Linear(config.d_model, 3 * config.d_model)
        self.proj = nn.Linear(config.d_model, config.d_model)
        self.dropout = nn.Dropout(config.dropout)

    def forward(self, x):
        batch, length, width = x.shape
        q, k, v = self.qkv(x).chunk(3, dim=-1)
        def split_heads(t):
            return t.view(batch, length, self.heads, self.head_dim).transpose(1, 2)
        q, k, v = map(split_heads, (q, k, v))
        scores = (q @ k.transpose(-2, -1)) * (self.head_dim ** -0.5)
        causal = torch.triu(
            torch.ones(length, length, device=x.device, dtype=torch.bool), diagonal=1
        )
        scores = scores.masked_fill(causal, torch.finfo(scores.dtype).min)
        weights = self.dropout(torch.softmax(scores, dim=-1))
        attended = weights @ v
        attended = attended.transpose(1, 2).contiguous().view(batch, length, width)
        return self.proj(attended)


class TransformerBlock(nn.Module):
    def __init__(self, config: BozerConfig):
        super().__init__()
        d = config.d_model
        hidden = d * config.mlp_ratio
        self.ln1 = nn.LayerNorm(d)
        self.attn = CausalSelfAttention(config)
        self.ln2 = nn.LayerNorm(d)
        self.mlp = nn.Sequential(
            nn.Linear(d, hidden), nn.GELU(),
            nn.Linear(hidden, d), nn.Dropout(config.dropout)
        )

    def forward(self, x):
        x = x + self.attn(self.ln1(x))
        return x + self.mlp(self.ln2(x))


class BozerTransformer(nn.Module):
    def __init__(self, config: BozerConfig | None = None):
        super().__init__()
        self.config = config or BozerConfig()
        c = self.config
        self.token_embedding = nn.Embedding(c.vocab_size, c.d_model)
        self.position_embedding = nn.Embedding(c.context_length, c.d_model)
        self.drop = nn.Dropout(c.dropout)
        self.blocks = nn.ModuleList([TransformerBlock(c) for _ in range(c.n_layers)])
        self.final_norm = nn.LayerNorm(c.d_model)
        self.lm_head = nn.Linear(c.d_model, c.vocab_size, bias=False)
        self.lm_head.weight = self.token_embedding.weight
        self.apply(self._init_weights)

    @staticmethod
    def _init_weights(module):
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    def forward(self, input_ids, targets=None):
        if input_ids.ndim != 2:
            raise ValueError("input_ids must have shape [batch, sequence]")
        _, length = input_ids.shape
        if length > self.config.context_length:
            raise ValueError("sequence exceeds configured context_length")
        positions = torch.arange(length, device=input_ids.device)
        x = self.drop(self.token_embedding(input_ids) + self.position_embedding(positions))
        for block in self.blocks:
            x = block(x)
        logits = self.lm_head(self.final_norm(x))
        loss = None
        if targets is not None:
            loss = nn.functional.cross_entropy(
                logits.reshape(-1, logits.size(-1)), targets.reshape(-1)
            )
        return logits, loss

    @torch.no_grad()
    def generate(self, input_ids, max_new_tokens=80, temperature=0.8, top_k=40):
        if temperature <= 0:
            raise ValueError("temperature must be positive")
        self.eval()
        for _ in range(max_new_tokens):
            context = input_ids[:, -self.config.context_length:]
            logits, _ = self(context)
            logits = logits[:, -1, :] / temperature
            if top_k and top_k < logits.size(-1):
                threshold = torch.topk(logits, top_k).values[:, -1, None]
                logits = logits.masked_fill(logits < threshold, float("-inf"))
            next_token = torch.multinomial(torch.softmax(logits, dim=-1), num_samples=1)
            input_ids = torch.cat((input_ids, next_token), dim=1)
        return input_ids
