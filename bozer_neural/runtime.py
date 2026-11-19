"""Checkpoint-backed inference entry point for BOZER NEURAL 1B.

A runtime only becomes usable when supplied with a trained checkpoint created by
train_1b.py. Randomly initialized models are not silently substituted.
"""
from __future__ import annotations
from pathlib import Path
import torch

from .config_1b import Bozer1BConfig
from .generation import GenerationConfig, generate
from .model_1b import BozerNeural1B
from .tokenizer import BozerByteBPETokenizer, EOS
from .train_1b import CHECKPOINT_FORMAT
from .retrieval import BozerRetrievalMemory


class BozerNeuralRuntime:
    def __init__(self, model: BozerNeural1B, tokenizer: BozerByteBPETokenizer,
                 *, device: str | torch.device = "cpu",
                 retrieval_memory: BozerRetrievalMemory | None = None):
        self.device = torch.device(device)
        if self.device.type == "cuda" and not torch.cuda.is_available():
            raise ValueError("CUDA was requested but is unavailable")
        if model.config.vocab_size != tokenizer.vocab_size:
            raise ValueError("model and tokenizer vocabulary sizes do not match")
        if tokenizer.active_vocab_size > model.config.vocab_size:
            raise ValueError("tokenizer active vocabulary exceeds model vocabulary")
        self.model = model.to(self.device).eval()
        self.tokenizer = tokenizer
        self.retrieval_memory = retrieval_memory

    @classmethod
    def from_checkpoint(cls, checkpoint_path: str | Path, *, device="cpu",
                        retrieval_corpus: str | Path | None = None):
        # weights_only prevents arbitrary Python objects from being unpickled.
        checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
        if not isinstance(checkpoint, dict) or checkpoint.get("format") != CHECKPOINT_FORMAT:
            raise ValueError("unsupported BOZER NEURAL checkpoint format")
        config_payload = checkpoint.get("config")
        tokenizer_payload = checkpoint.get("tokenizer")
        state = checkpoint.get("model_state_dict")
        if not isinstance(config_payload, dict) or not isinstance(tokenizer_payload, dict):
            raise ValueError("checkpoint is missing its model config or tokenizer")
        if not isinstance(state, dict):
            raise ValueError("checkpoint is missing model weights")
        config = Bozer1BConfig.from_dict(config_payload)
        tokenizer = BozerByteBPETokenizer.from_dict(tokenizer_payload)
        if tokenizer.vocab_size != config.vocab_size:
            raise ValueError("checkpoint model/tokenizer vocabulary mismatch")
        model = BozerNeural1B(config)
        model.load_state_dict(state, strict=True)
        memory = (BozerRetrievalMemory.from_corpus(retrieval_corpus)
                  if retrieval_corpus is not None else None)
        return cls(model, tokenizer, device=device, retrieval_memory=memory)

    @torch.inference_mode()
    def complete(self, prompt: str, config: GenerationConfig | None = None) -> str:
        if not isinstance(prompt, str) or not prompt.strip():
            raise ValueError("prompt must not be empty")
        if self.retrieval_memory is not None:
            retrieval_query = prompt
            if "Utilisateur :" in retrieval_query:
                retrieval_query = retrieval_query.rsplit("Utilisateur :", 1)[-1]
                retrieval_query = retrieval_query.split("\nBOZER :", 1)[0]
            retrieved = self.retrieval_memory.retrieve(retrieval_query.strip())
            if retrieved is not None:
                return retrieved["answer"]
        token_ids = self.tokenizer.encode(prompt)[:-1]
        token_ids = token_ids[-self.model.config.context_length:]
        prefix = torch.tensor([token_ids], dtype=torch.long, device=self.device)
        settings = config or GenerationConfig(
            max_new_tokens=96,
            active_vocab_size=self.tokenizer.active_vocab_size,
            eos_token_id=EOS,
        )
        if settings.active_vocab_size not in (None, self.tokenizer.active_vocab_size):
            raise ValueError("generation active vocabulary differs from checkpoint tokenizer")
        if settings.active_vocab_size is None:
            from dataclasses import replace
            settings = replace(settings, active_vocab_size=self.tokenizer.active_vocab_size)
        generated = generate(self.model, prefix, settings)[0].tolist()
        completion = generated[len(token_ids):]
        if EOS in completion:
            completion = completion[:completion.index(EOS)]
        return self.tokenizer.decode(completion)
