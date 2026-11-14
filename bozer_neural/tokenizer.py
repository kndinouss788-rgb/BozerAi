"""A dependency-free UTF-8 byte tokenizer; no downloaded vocabulary."""
PAD, BOS, EOS = 0, 1, 2
BYTE_OFFSET = 3


class BozerByteTokenizer:
    vocab_size = 259

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
