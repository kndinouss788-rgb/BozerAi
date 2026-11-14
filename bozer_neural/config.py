"""Model configuration for BOZER NEURAL V0.1."""
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class BozerConfig:
    vocab_size: int = 259  # PAD, BOS, EOS + all 256 UTF-8 byte values
    context_length: int = 128
    d_model: int = 64
    n_layers: int = 2
    n_heads: int = 4
    mlp_ratio: int = 4
    dropout: float = 0.0

    def __post_init__(self):
        if self.d_model % self.n_heads:
            raise ValueError("d_model must be divisible by n_heads")
        if self.context_length < 2 or self.n_layers < 1:
            raise ValueError("context_length >= 2 and n_layers >= 1 are required")
        if self.vocab_size != 259:
            raise ValueError("V0.1 byte tokenizer requires vocab_size=259")

    def to_dict(self):
        return asdict(self)

    def parameter_count(self):
        # Tied token/output embeddings; learned absolute position embeddings.
        d, v, c, r, n = (
            self.d_model, self.vocab_size, self.context_length,
            self.mlp_ratio, self.n_layers
        )
        embeddings = v * d + c * d
        per_block = (
            2 * (2 * d)                 # two LayerNorms (weight + bias)
            + (3 * d * d + 3 * d)       # fused QKV projection
            + (d * d + d)               # attention output projection
            + (d * r * d + r * d)       # MLP expansion
            + (r * d * d + d)           # MLP projection
        )
        final_norm = 2 * d
        return embeddings + n * per_block + final_norm
