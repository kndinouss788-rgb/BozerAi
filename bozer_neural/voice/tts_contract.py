"""Model-agnostic contract for BOZER's original TTS voice.

No synthesizer or voice checkpoint is bundled here. A runtime adapter must
supply lawful model weights and pass device, pronunciation and latency tests.
"""
from dataclasses import dataclass
from typing import Protocol, Iterable


SUPPORTED_LANGUAGES = {"fr-FR", "en-US", "mos-BF"}


@dataclass(frozen=True)
class SpeechRequest:
    text: str
    language: str = "fr-FR"
    style: str = "conversational"
    rate: float = 1.0
    interruptible: bool = True

    def __post_init__(self):
        if not self.text.strip():
            raise ValueError("speech text cannot be empty")
        if self.language not in SUPPORTED_LANGUAGES:
            raise ValueError("unsupported speech language")
        if not 0.65 <= self.rate <= 1.5:
            raise ValueError("rate must be between 0.65 and 1.5")
        if not self.style.strip():
            raise ValueError("speech style must not be empty")


@dataclass(frozen=True)
class AudioChunk:
    """Signed 16-bit little-endian mono PCM chunk."""
    pcm: bytes
    sample_rate: int
    final: bool = False

    def __post_init__(self):
        if not isinstance(self.pcm, bytes) or not self.pcm:
            raise ValueError("audio chunk must contain PCM bytes")
        if self.sample_rate not in {16000, 22050, 24000, 44100, 48000}:
            raise ValueError("unsupported PCM sample rate")
        if len(self.pcm) % 2:
            raise ValueError("16-bit PCM byte length must be even")


class TTSBackend(Protocol):
    def synthesize(self, request: SpeechRequest) -> Iterable[AudioChunk]: ...


class SpeechController:
    """Single-utterance lifecycle wrapper around a concrete backend."""
    def __init__(self, backend: TTSBackend):
        self.backend = backend
        self._generation = 0

    def cancel(self) -> None:
        self._generation += 1

    def stream(self, request: SpeechRequest):
        generation = self._generation
        for chunk in self.backend.synthesize(request):
            if generation != self._generation:
                return
            if not isinstance(chunk, AudioChunk):
                raise TypeError("TTS backend must yield AudioChunk values")
            yield chunk
