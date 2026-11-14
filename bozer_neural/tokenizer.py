"""Byte and corpus-trained byte-pair tokenizers; no pretrained vocabulary."""
PAD, BOS, EOS = 0, 1, 2
BYTE_OFFSET = 3
BASE_VOCAB_SIZE = 259


class BozerByteTokenizer:
    """Stable fallback tokenizer: one token per UTF-8 byte."""

    vocab_size = BASE_VOCAB_SIZE

    def encode(self, text: str, add_special_tokens: bool = True):
        ids = [b + BYTE_OFFSET for b in text.encode("utf-8")]
        return ([BOS] + ids + [EOS]) if add_special_tokens else ids

    def decode(self, ids, skip_special_tokens: bool = True):
        raw = bytearray()
        for token in ids:
            token = int(token)
            if token in (PAD, BOS, EOS):
                if skip_special_tokens:
                    continue
                continue
            if BYTE_OFFSET <= token < self.vocab_size:
                raw.append(token - BYTE_OFFSET)
            else:
                raise ValueError(f"token id out of range: {token}")
        return raw.decode("utf-8", errors="replace")


class BozerByteBPETokenizer:
    """Byte-level BPE vocabulary trained only from the supplied BOZER corpus."""

    def __init__(self, merges=(), padded_vocab_size=None):
        self.merges = [(int(left), int(right)) for left, right in merges]
        self.active_vocab_size = BASE_VOCAB_SIZE + len(self.merges)
        self.vocab_size = (
            self.active_vocab_size if padded_vocab_size is None
            else int(padded_vocab_size)
        )
        if self.vocab_size < self.active_vocab_size:
            raise ValueError("padded vocabulary cannot be smaller than active vocabulary")
        self._expansions = {
            BASE_VOCAB_SIZE + index: pair
            for index, pair in enumerate(self.merges)
        }

    @classmethod
    def train(cls, text: str, vocab_size: int = 512, min_frequency: int = 2,
              pad_to_vocab_size=None):
        if vocab_size < BASE_VOCAB_SIZE:
            raise ValueError(f"vocab_size must be at least {BASE_VOCAB_SIZE}")
        if min_frequency < 2:
            raise ValueError("min_frequency must be >= 2")
        sequences = [
            [value + BYTE_OFFSET for value in line.encode("utf-8")]
            for line in text.splitlines() if line.strip()
        ]
        if not sequences:
            raise ValueError("cannot train tokenizer on empty text")

        merges = []
        while BASE_VOCAB_SIZE + len(merges) < vocab_size:
            counts = {}
            for sequence in sequences:
                for pair in zip(sequence, sequence[1:]):
                    counts[pair] = counts.get(pair, 0) + 1
            if not counts:
                break
            pair, frequency = max(counts.items(), key=lambda item: (item[1], item[0]))
            if frequency < min_frequency:
                break

            new_token = BASE_VOCAB_SIZE + len(merges)
            left, right = pair
            for index, sequence in enumerate(sequences):
                merged = []
                cursor = 0
                while cursor < len(sequence):
                    if cursor + 1 < len(sequence) and sequence[cursor] == left and sequence[cursor + 1] == right:
                        merged.append(new_token)
                        cursor += 2
                    else:
                        merged.append(sequence[cursor])
                        cursor += 1
                sequences[index] = merged
            merges.append(pair)
        return cls(merges, padded_vocab_size=pad_to_vocab_size)

    def encode(self, text: str, add_special_tokens: bool = True):
        sequence = [value + BYTE_OFFSET for value in text.encode("utf-8")]
        for index, (left, right) in enumerate(self.merges):
            new_token = BASE_VOCAB_SIZE + index
            merged = []
            cursor = 0
            while cursor < len(sequence):
                if cursor + 1 < len(sequence) and sequence[cursor] == left and sequence[cursor + 1] == right:
                    merged.append(new_token)
                    cursor += 2
                else:
                    merged.append(sequence[cursor])
                    cursor += 1
            sequence = merged
        return ([BOS] + sequence + [EOS]) if add_special_tokens else sequence

    def decode(self, ids, skip_special_tokens: bool = True):
        raw = bytearray()

        def append_bytes(token):
            if BYTE_OFFSET <= token < BASE_VOCAB_SIZE:
                raw.append(token - BYTE_OFFSET)
            elif token in self._expansions:
                left, right = self._expansions[token]
                append_bytes(left)
                append_bytes(right)
            elif self.active_vocab_size <= token < self.vocab_size:
                # Reserved vocabulary rows are masked during generation and
                # safely ignored if an external checkpoint nevertheless emits one.
                return
            else:
                raise ValueError(f"token id out of range: {token}")

        for value in ids:
            token = int(value)
            if token in (PAD, BOS, EOS):
                if skip_special_tokens:
                    continue
                continue
            append_bytes(token)
        return raw.decode("utf-8", errors="replace")

    def to_dict(self):
        payload = {
            "type": "byte_bpe",
            "base_vocab_size": BASE_VOCAB_SIZE,
            "vocab_size": self.vocab_size,
            "merges": [list(pair) for pair in self.merges],
        }
        if self.active_vocab_size != self.vocab_size:
            payload["active_vocab_size"] = self.active_vocab_size
        return payload

    @classmethod
    def from_dict(cls, payload):
        if payload.get("type") != "byte_bpe" or payload.get("base_vocab_size") != BASE_VOCAB_SIZE:
            raise ValueError("unsupported BOZER tokenizer format")
        merges = payload.get("merges", [])
        active_vocab_size = BASE_VOCAB_SIZE + len(merges)
        if payload.get("active_vocab_size", active_vocab_size) != active_vocab_size:
            raise ValueError("tokenizer active vocabulary size does not match its merges")
        tokenizer = cls(merges, padded_vocab_size=payload.get("vocab_size"))
        for index, (left, right) in enumerate(tokenizer.merges):
            upper_bound = BASE_VOCAB_SIZE + index
            if not (0 <= left < upper_bound and 0 <= right < upper_bound):
                raise ValueError("tokenizer merge references an unknown or future token")
        return tokenizer
