"""Model-agnostic contract for BOZER's original TTS voice.

No synthesizer or voice checkpoint is bundled here. A runtime adapter must
supply lawful model weights and pass device, pronunciation and latency tests.
"""
from dataclasses import dataclass
from typing import Protocol, Iterable

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
        if not 0.65 <= self.rate <= 1.5:
            raise ValueError("rate must be between 0.65 and 1.5")

@dataclass(frozen=True)
class AudioChunk:
    pcm: bytes
    sample_rate: int
    final: bool = False

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
            if not chunk.pcm or chunk.sample_rate <= 0:
                raise ValueError("backend emitted invalid audio chunk")
            yield chunk
