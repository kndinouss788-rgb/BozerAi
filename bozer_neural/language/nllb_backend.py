"""Optional local NLLB-compatible inference backend for French <-> Mooré.

No model weights are bundled. A checkpoint must be supplied by the deployer and
must have an explicitly cleared commercial-use license before BOZER uses it.
"""
from __future__ import annotations

from typing import Any


LANGUAGE_CODES = {"fr": "fra_Latn", "mos": "mos_Latn"}


class TransformersNllbBackend:
    """Lazy local seq2seq backend compatible with NLLB tokenizer/model APIs.

    The caller must affirm commercial rights for the exact checkpoint. This
    deliberately prevents BOZER from silently selecting research-only weights.
    """

    def __init__(
        self,
        tokenizer: Any,
        model: Any,
        *,
        model_id: str,
        license_id: str,
        commercial_use_allowed: bool,
        device: str = "cpu",
        max_input_tokens: int = 480,
        num_beams: int = 3,
    ) -> None:
        if not commercial_use_allowed:
            raise ValueError("BOZER requires a checkpoint cleared for commercial use")
        if not license_id or not license_id.strip():
            raise ValueError("the checkpoint license identifier must be recorded")
        if not model_id or not model_id.strip():
            raise ValueError("model_id must identify the supplied checkpoint")
        if max_input_tokens < 1 or num_beams < 1:
            raise ValueError("generation limits must be positive")
        self.tokenizer = tokenizer
        self.model = model
        self.model_id = model_id
        self.license_id = license_id
        self.device = device
        self.max_input_tokens = max_input_tokens
        self.num_beams = num_beams
        self.model.to(device)
        self.model.eval()

    @classmethod
    def from_pretrained(
        cls,
        model_id: str,
        *,
        license_id: str,
        commercial_use_allowed: bool,
        device: str = "cpu",
        max_input_tokens: int = 480,
        num_beams: int = 3,
    ) -> "TransformersNllbBackend":
        if not commercial_use_allowed:
            raise ValueError("BOZER requires a checkpoint cleared for commercial use")
        try:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
        except ImportError as exc:
            raise RuntimeError(
                "Install the optional 'transformers' package to load a local translation model"
            ) from exc
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        model = AutoModelForSeq2SeqLM.from_pretrained(model_id)
        return cls(
            tokenizer,
            model,
            model_id=model_id,
            license_id=license_id,
            commercial_use_allowed=True,
            device=device,
            max_input_tokens=max_input_tokens,
            num_beams=num_beams,
        )

    def translate(self, text: str, source_language: str, target_language: str) -> str:
        source_code = LANGUAGE_CODES.get(source_language)
        target_code = LANGUAGE_CODES.get(target_language)
        if not source_code or not target_code or source_language == target_language:
            raise ValueError("backend supports only French <-> Mooré")
        if not isinstance(text, str) or not text.strip():
            raise ValueError("text must not be empty")

        self.tokenizer.src_lang = source_code
        encoded = self.tokenizer(text, return_tensors="pt", truncation=False)
        input_ids = encoded.get("input_ids")
        if input_ids is None:
            raise RuntimeError("tokenizer did not return input_ids")
        token_count = int(input_ids.shape[-1])
        if token_count > self.max_input_tokens:
            raise ValueError(
                f"input has {token_count} tokens; maximum is {self.max_input_tokens}; "
                "split the text at sentence boundaries instead of silently truncating it"
            )
        if hasattr(encoded, "to"):
            encoded = encoded.to(self.device)
        target_id = self.tokenizer.convert_tokens_to_ids(target_code)
        if target_id is None or target_id == getattr(self.tokenizer, "unk_token_id", None):
            raise RuntimeError(f"checkpoint tokenizer does not support {target_code}")

        try:
            import torch
            inference_context = torch.inference_mode()
        except ImportError:
            from contextlib import nullcontext
            inference_context = nullcontext()

        with inference_context:
            output = self.model.generate(
                **encoded,
                forced_bos_token_id=target_id,
                num_beams=self.num_beams,
                do_sample=False,
                max_new_tokens=128,
            )
        decoded = self.tokenizer.batch_decode(output, skip_special_tokens=True)[0]
        translated = decoded.strip()
        if not translated:
            raise RuntimeError("translation model returned an empty result")
        return translated
