"""Objective PCM WAV quality metrics for BOZER voice recording intake.

Metrics flag obvious recording defects; they cannot assess pronunciation,
consent, speaker identity, naturalness or legal permission.
"""
from __future__ import annotations
from dataclasses import dataclass, asdict
import math
import wave
from pathlib import Path


@dataclass(frozen=True)
class AudioQualityReport:
    sample_rate: int
    channels: int
    sample_width_bits: int
    duration_seconds: float
    peak_dbfs: float
    rms_dbfs: float
    clipped_sample_percent: float
    near_silence_percent: float
    warnings: tuple[str, ...]

    def to_dict(self):
        return asdict(self)


def analyze_wav_quality(path: str | Path) -> AudioQualityReport:
    path = Path(path)
    with wave.open(str(path), "rb") as audio:
        channels = audio.getnchannels()
        width = audio.getsampwidth()
        rate = audio.getframerate()
        frames = audio.getnframes()
        if audio.getcomptype() != "NONE" or channels != 1:
            raise ValueError("quality analyzer requires uncompressed mono PCM WAV")
        if width not in {2, 3} or rate <= 0 or frames <= 0:
            raise ValueError("unsupported or empty PCM WAV")
        raw = audio.readframes(frames)

    if width == 2:
        if len(raw) % 2:
            raise ValueError("truncated 16-bit PCM data")
        values = [int.from_bytes(raw[i:i + 2], "little", signed=True)
                  for i in range(0, len(raw), 2)]
        full_scale = 32768
    else:
        if len(raw) % 3:
            raise ValueError("truncated 24-bit PCM data")
        values = []
        for i in range(0, len(raw), 3):
            sample = int.from_bytes(raw[i:i + 3], "little", signed=False)
            if sample & 0x800000:
                sample -= 1 << 24
            values.append(sample)
        full_scale = 1 << 23

    peak = max(abs(value) for value in values) / full_scale
    mean_square = sum((value / full_scale) ** 2 for value in values) / len(values)
    rms = math.sqrt(mean_square)
    clipped = sum(abs(value) >= full_scale - 1 for value in values) / len(values) * 100
    near_silence = sum(abs(value) <= full_scale * 0.001 for value in values) / len(values) * 100

    def dbfs(value):
        return 20 * math.log10(value) if value > 0 else float("-inf")

    warnings = []
    if peak < 0.05:
        warnings.append("very_low_peak_level")
    if rms < 0.005:
        warnings.append("very_low_rms_level")
    if clipped > 0.1:
        warnings.append("clipping_detected")
    if near_silence > 80:
        warnings.append("mostly_silence")
    return AudioQualityReport(
        sample_rate=rate,
        channels=channels,
        sample_width_bits=width * 8,
        duration_seconds=round(frames / rate, 3),
        peak_dbfs=round(dbfs(peak), 2),
        rms_dbfs=round(dbfs(rms), 2),
        clipped_sample_percent=round(clipped, 4),
        near_silence_percent=round(near_silence, 3),
        warnings=tuple(warnings),
    )
