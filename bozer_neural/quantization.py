"""Portable INT8/INT4 checkpoint export for BOZER NEURAL.

This packs model weights for smaller storage. Runtime dequantization is provided
for parity/testing; it is not a native integer-operator inference backend.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import json

import torch

from .train_1b import CHECKPOINT_FORMAT


QUANTIZED_CHECKPOINT_FORMAT = "bozer-neural-quantized-v1"


def _quantize_tensor(tensor: torch.Tensor, bits: int) -> dict:
    if bits not in (4, 8):
        raise ValueError("bits must be 4 or 8")
    source = tensor.detach().to(device="cpu", dtype=torch.float32).contiguous()
    if not torch.isfinite(source).all():
        raise ValueError("model weights must contain only finite values")
    qmin, qmax = (-(2 ** (bits - 1)), (2 ** (bits - 1)) - 1)
    scale = source.abs().max() / qmax
    if not bool(scale > 0):
        scale = torch.tensor(1.0, dtype=torch.float32)
    quantized = torch.round(source / scale).clamp(qmin, qmax).to(torch.int8)
    flat = quantized.reshape(-1)
    if bits == 8:
        payload = flat
    else:
        # Two signed 4-bit values per byte; odd tensors receive one zero pad nibble.
        if flat.numel() % 2:
            flat = torch.cat((flat, torch.zeros(1, dtype=torch.int8)))
        nibbles = (flat.to(torch.int16) & 0x0F).to(torch.uint8)
        payload = nibbles[0::2] | (nibbles[1::2] << 4)
    return {
        "bits": bits,
        "shape": tuple(source.shape),
        "scale": scale.reshape(()),
        "payload": payload.contiguous(),
        "numel": source.numel(),
    }


def _dequantize_tensor(record: dict) -> torch.Tensor:
    bits = record.get("bits")
    shape = record.get("shape")
    numel = record.get("numel")
    payload = record.get("payload")
    scale = record.get("scale")
    if bits not in (4, 8) or not isinstance(payload, torch.Tensor):
        raise ValueError("invalid quantized tensor record")
    if not isinstance(scale, torch.Tensor) or scale.numel() != 1:
        raise ValueError("quantized tensor is missing its scalar scale")
    if not isinstance(shape, (tuple, list)) or not isinstance(numel, int):
        raise ValueError("quantized tensor shape metadata is invalid")
    if any(not isinstance(dim, int) or dim < 0 for dim in shape):
        raise ValueError("quantized tensor shape is invalid")
    expected = 1
    for dim in shape:
        expected *= dim
    if expected != numel:
        raise ValueError("quantized tensor element count does not match shape")
    if bits == 8:
        values = payload.to(torch.int8).reshape(-1)
    else:
        packed = payload.to(torch.uint8).reshape(-1)
        low = (packed & 0x0F).to(torch.int16)
        high = ((packed >> 4) & 0x0F).to(torch.int16)
        values = torch.stack((low, high), dim=1).reshape(-1)[:numel]
        values = torch.where(values >= 8, values - 16, values).to(torch.int8)
    if values.numel() < numel:
        raise ValueError("quantized payload is shorter than its declared shape")
    return (values[:numel].to(torch.float32) * scale.to(torch.float32)).reshape(tuple(shape))


def export_quantized_checkpoint(
    checkpoint_path: str | Path,
    output_path: str | Path,
    *,
    bits: int = 8,
) -> dict:
    """Export a trained BOZER checkpoint as a packed INT8 or INT4 artifact."""
    if bits not in (4, 8):
        raise ValueError("bits must be 4 or 8")
    source = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
    if not isinstance(source, dict) or source.get("format") != CHECKPOINT_FORMAT:
        raise ValueError("unsupported BOZER NEURAL source checkpoint")
    training = source.get("training")
    if not isinstance(training, dict) or int(training.get("global_step", 0)) < 1:
        raise ValueError("refusing to quantize an untrained or initial checkpoint")
    state = source.get("model_state_dict")
    if not isinstance(state, dict) or not state:
        raise ValueError("source checkpoint is missing model weights")

    packed = {}
    for name, tensor in state.items():
        if not isinstance(name, str) or not isinstance(tensor, torch.Tensor):
            raise ValueError("model state must map tensor names to tensors")
        packed[name] = _quantize_tensor(tensor, bits)

    artifact = {
        "format": QUANTIZED_CHECKPOINT_FORMAT,
        "source_format": CHECKPOINT_FORMAT,
        "bits": bits,
        "config": source.get("config"),
        "tokenizer": source.get("tokenizer"),
        "training": {
            "global_step": int(training["global_step"]),
            "training_objective": training.get("training_objective"),
            "best_validation_loss": training.get("best_validation_loss"),
        },
        "model_state_quantized": packed,
    }
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    torch.save(artifact, temporary)
    temporary.replace(destination)
    return {
        "output": str(destination),
        "bits": bits,
        "tensor_count": len(packed),
        "source_bytes": Path(checkpoint_path).stat().st_size,
        "quantized_bytes": destination.stat().st_size,
        "global_step": int(training["global_step"]),
    }


def load_quantized_state_dict(artifact: dict) -> dict[str, torch.Tensor]:
    if not isinstance(artifact, dict) or artifact.get("format") != QUANTIZED_CHECKPOINT_FORMAT:
        raise ValueError("unsupported BOZER quantized checkpoint format")
    packed = artifact.get("model_state_quantized")
    if not isinstance(packed, dict) or not packed:
        raise ValueError("quantized checkpoint is missing model tensors")
    return {name: _dequantize_tensor(record) for name, record in packed.items()}


def main():
    parser = argparse.ArgumentParser(description="Export a trained BOZER checkpoint to packed INT8/INT4")
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--bits", type=int, choices=(4, 8), default=8)
    args = parser.parse_args()
    print(json.dumps(
        export_quantized_checkpoint(args.checkpoint, args.output, bits=args.bits),
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()
