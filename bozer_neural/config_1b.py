"""Production-shape BOZER NEURAL 1B configuration (random-init architecture)."""
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class Bozer1BConfig:
    """Target 1.116B decoder-only Transformer; defaults are intentionally large."""

    vocab_size: int = 16_384
    context_length: int = 2_048
    d_model: int = 2_048
    n_layers: int = 24
    n_query_heads: int = 16
    n_kv_heads: int = 4
    head_dim: int = 128
    ffn_dim: int = 5_632
    rope_theta: float = 10_000.0
    rms_norm_eps: float = 1e-6
    dropout: float = 0.0

    def __post_init__(self):
        positive = (
            "vocab_size", "context_length", "d_model", "n_layers",
            "n_query_heads", "n_kv_heads", "head_dim", "ffn_dim",
        )
        for name in positive:
            if getattr(self, name) < 1:
                raise ValueError(f"{name} must be positive")
        if self.vocab_size < 259:
            raise ValueError("vocab_size must include the 259 base byte/special tokens")
        if self.d_model != self.n_query_heads * self.head_dim:
            raise ValueError("d_model must equal n_query_heads * head_dim")
        if self.n_query_heads % self.n_kv_heads:
            raise ValueError("n_query_heads must be divisible by n_kv_heads for GQA")
        if self.head_dim % 2:
            raise ValueError("head_dim must be even for RoPE")
        if self.rope_theta <= 1:
            raise ValueError("rope_theta must be greater than 1")
        if self.rms_norm_eps <= 0:
            raise ValueError("rms_norm_eps must be positive")
        if not 0.0 <= self.dropout < 1.0:
            raise ValueError("dropout must be in the range [0, 1)")

    @property
    def kv_dim(self):
        return self.n_kv_heads * self.head_dim

    @property
    def gqa_groups(self):
        return self.n_query_heads // self.n_kv_heads

    def to_dict(self):
        return asdict(self)

    def parameter_count(self):
        """Exact count for bias-free Q/K/V/O + SwiGLU, RMSNorm and tied embeddings."""
        d = self.d_model
        kv = self.kv_dim
        f = self.ffn_dim
        embeddings = self.vocab_size * d
        # Q and O: d*d each; K and V: d*kv each.
        attention = 2 * d * d + 2 * d * kv
        # SwiGLU: gate, up and down projections.
        swiglu = 3 * d * f
        # Two pre-norm scale vectors per block.
        block_norms = 2 * d
        final_norm = d
        return embeddings + self.n_layers * (
            attention + swiglu + block_norms
        ) + final_norm

    @classmethod
    def from_dict(cls, payload):
        if not isinstance(payload, dict):
            raise TypeError("configuration payload must be a dictionary")
        return cls(**payload)
