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

    def weight_storage_bytes(self, bits_per_parameter=16):
        """Raw model-weight size estimate; excludes quantization metadata and file overhead."""
        if bits_per_parameter not in (4, 8, 16, 32):
            raise ValueError("bits_per_parameter must be one of 4, 8, 16 or 32")
        return (self.parameter_count() * bits_per_parameter + 7) // 8

    def kv_cache_bytes(self, batch_size=1, context_length=None,
                       bytes_per_element=2):
        """KV-cache estimate for inference, assuming the configured GQA layout."""
        length = self.context_length if context_length is None else context_length
        if batch_size < 1 or length < 1 or length > self.context_length:
            raise ValueError("batch_size and context_length must be within valid limits")
        if bytes_per_element not in (2, 4):
            raise ValueError("bytes_per_element must be 2 (FP16/BF16) or 4 (FP32)")
        return (
            batch_size * 2 * self.n_layers * self.n_kv_heads
            * self.head_dim * length * bytes_per_element
        )

    def inference_storage_estimates(self, context_length=None):
        """Return raw weight and KV-cache estimates, not measured runtime memory."""
        return {
            "parameters": self.parameter_count(),
            "weights_fp32_bytes": self.weight_storage_bytes(32),
            "weights_fp16_bytes": self.weight_storage_bytes(16),
            "weights_int8_bytes": self.weight_storage_bytes(8),
            "weights_int4_raw_bytes": self.weight_storage_bytes(4),
            "kv_cache_fp16_bytes": self.kv_cache_bytes(
                context_length=context_length, bytes_per_element=2
            ),
        }

    @classmethod
    def from_dict(cls, payload):
        if not isinstance(payload, dict):
            raise TypeError("configuration payload must be a dictionary")
        return cls(**payload)
