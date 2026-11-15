"""Conservative text frontend and chunking for BOZER TTS."""
from __future__ import annotations
from dataclasses import dataclass
import re
from .pronunciation import normalize_for_speech


@dataclass(frozen=True)
class SpeechSegment:
    index: int
    text: str
    pause_after_ms: int


class SpeechTextFrontend:
    """Prepare readable chunks while preserving words, diacritics and punctuation.

    This component does not generate phonemes or guess Mooré pronunciation.
    """

    def __init__(self, max_characters: int = 220):
        if max_characters < 20:
            raise ValueError("max_characters must be at least 20")
        self.max_characters = max_characters

    @staticmethod
    def _pause(text: str) -> int:
        ending = text.rstrip()[-1:] if text.strip() else ""
        if ending in {".", "!", "?", "…"}:
            return 520
        if ending in {";", ":"}:
            return 300
        if ending == ",":
            return 160
        return 0

    def prepare(self, text: str, language: str) -> tuple[SpeechSegment, ...]:
        normalized = normalize_for_speech(text, language)
        if not normalized:
            return ()
        # Sentence-aware splitting. Punctuation remains attached to its sentence.
        sentences = re.findall(r".+?(?:[.!?…]+(?=\s|$)|$)", normalized)
        sentences = [part.strip() for part in sentences if part.strip()]
        packed: list[str] = []
        current = ""
        for sentence in sentences:
            if len(sentence) > self.max_characters:
                words = sentence.split()
                for word in words:
                    if len(word) > self.max_characters:
                        if current:
                            packed.append(current)
                            current = ""
                        for offset in range(0, len(word), self.max_characters):
                            packed.append(word[offset:offset + self.max_characters])
                    elif not current:
                        current = word
                    elif len(current) + 1 + len(word) <= self.max_characters:
                        current += " " + word
                    else:
                        packed.append(current)
                        current = word
                continue
            if not current:
                current = sentence
            elif len(current) + 1 + len(sentence) <= self.max_characters:
                current += " " + sentence
            else:
                packed.append(current)
                current = sentence
        if current:
            packed.append(current)
        return tuple(
            SpeechSegment(i, value, self._pause(value))
            for i, value in enumerate(packed)
        )
