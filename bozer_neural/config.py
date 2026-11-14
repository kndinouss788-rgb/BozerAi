"""Model configuration for BOZER NEURAL."""
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class BozerConfig:
    vocab_size: int = 259
    context_length: int = 128
    d_model: int = 64
    n_layers: int = 2
    n_heads: int = 4
    mlp_ratio: int = 4
    dropout: float = 0.0

    def __post_init__(self):
        if self.vocab_size < 259:
            raise ValueError("vocab_size must include the 259 base byte/special tokens")
        if self.d_model % self.n_heads:
            raise ValueError("d_model must be divisible by n_heads")
        if self.context_length < 2 or self.n_layers < 1:
            raise ValueError("context_length >= 2 and n_layers >= 1 are required")

    def to_dict(self):
        return asdict(self)

    def parameter_count(self):
        d, v, c, r, n = (
            self.d_model, self.vocab_size, self.context_length,
            self.mlp_ratio, self.n_layers
        )
        embeddings = v * d + c * d
        per_block = (
            2 * (2 * d)
            + (3 * d * d + 3 * d)
            + (d * d + d)
            + (d * r * d + r * d)
            + (r * d * d + d)
        )
        return embeddings + n * per_block + 2 * d
