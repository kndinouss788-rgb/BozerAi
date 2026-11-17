# BOZER French–Mooré neural model selection

Status checked: 17 November 2026.

## Decision

BOZER now has a dictionary-first pipeline and a pluggable local NLLB-compatible
sentence backend, but no model checkpoint is selected or bundled. Do not mark
sentence translation production-ready until the exact weights and their training
data have a documented commercial-use grant and a native-speaker evaluation.

## Checkpoints investigated

| Checkpoint | Evidence | BOZER decision |
|---|---|---|
| `burkimbia/BIA-MISTRAL-7B-SACHI_4bit` | Hugging Face lists Apache-2.0. The model is gated; its 4-bit weights are 7.2 GB and the card states a 12 GB minimum GPU. | Commercial license is promising, but size and GPU requirement make it unsuitable for the target Android phones. Could be considered for a separately hosted server after access and deployment are agreed. |
| `burkimbia/BIA-NLLB-600M-11E` | The card describes a 0.6B seq2seq model and provides Transformers loading instructions, but its license and training-data provenance are not specified in the visible model card. | Technically the closest fit for the NLLB adapter, but do not use in BOZER commercially until the publisher confirms rights in writing. |
| `Rekin226/nllb-600M-moore-lora-v0` | Model card explicitly declares CC-BY-NC-4.0 and explains the research-only source-data issue. | Excluded from BOZER commercial use. |
| `SuzuyaXIII/Faso-no-reesa-NLLB-LoRA` | Model card explicitly declares CC-BY-NC-4.0, including the NLLB non-commercial restriction. | Excluded from BOZER commercial use. |

## Research evidence

A 2026 ACL LoResLM paper reports a French–Mooré corpus of approximately
83,000 validated sentence pairs, selected from 97,060 pairs after filtering
and human evaluation. Its fine-tuned NLLB model outperformed the compared
mBART50 and AfroLM systems. The paper is evidence that sentence-level MT is
feasible; it is not, by itself, a commercial license for model weights or data.

## Required before production

1. Obtain written commercial-use confirmation for a small French–Mooré model,
   or train a BOZER model from data with explicit commercial redistribution rights.
2. Preserve source-level provenance and exclude any non-commercial or unclear
   source from commercial training.
3. Build separate held-out French→Mooré and Mooré→French test sets.
4. Have native Mooré speakers review naturalness, meaning, dialect and spelling.
5. Measure BLEU/chrF++ plus human adequacy; publish the measured results and
   known failure modes rather than claiming 100% linguistic accuracy.
6. Benchmark latency, RAM and model size on representative Android devices
   before choosing on-device inference. A server backend is a separate option.

## Sources

- BurkimbIA BIA-MISTRAL model card: https://huggingface.co/burkimbia/BIA-MISTRAL-7B-SACHI_4bit
- BurkimbIA NLLB-600M model card: https://huggingface.co/burkimbia/BIA-NLLB-600M-11E
- Rekin226 NLLB Mooré LoRA: https://huggingface.co/Rekin226/nllb-600M-moore-lora-v0
- Faso no-rɛɛsa NLLB LoRA: https://huggingface.co/SuzuyaXIII/Faso-no-reesa-NLLB-LoRA
- ACL LoResLM 2026 paper: https://aclanthology.org/2026.loreslm-1.53/
