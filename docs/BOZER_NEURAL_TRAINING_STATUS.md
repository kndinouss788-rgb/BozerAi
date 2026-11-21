# BOZER NEURAL — chantier d'entraînement archivé

> **Statut au 21 novembre 2026 : ARCHIVÉ.** Le modèle 1B personnalisé n'a jamais été entraîné sur GPU et n'est plus la voie active du cerveau Android. Le projet a été conservé sur la branche `archive/bozer-neural-training` pour préserver le travail et les données.

La voie active est désormais le modèle pré-entraîné **Qwen2.5-0.5B-Instruct GGUF**, exécuté localement par llama.cpp et encadré par l'identité, la mémoire, les règles de fiabilité et les outils Android de BOZER. Le mode Automatique privilégie le modèle local ; l'inférence distante est réservée au mode En ligne explicite.

Le code expérimental d'entraînement, les corpus et les rapports ci-dessous sont conservés comme archive technique et ne doivent pas être présentés comme un cerveau BOZER entraîné ou opérationnel.

---

Branch: `bozer-neural-v0.1`

## What exists today

- `bozer_neural.model.BozerTransformer`: a small decoder-only Transformer with 124,864 trainable parameters at its default configuration. Its weights are randomly initialized.
- `bozer_neural.train`: supervised training and checkpoint-resume implementation. It learns a BPE tokenizer from the training split, optimizes assistant-answer tokens plus EOS (not the user's prompt), uses AdamW, gradient accumulation, learning-rate warmup/cosine decay, validation loss, early stopping, and atomic checkpoints.
- `bozer_neural.evaluate`: evaluates a saved checkpoint against a separate test split.
- `bozer_neural.learning`: feedback is queued first; only explicitly approved examples can be added to a training file.
- `bozer_neural.model_1b.BozerNeural1B`: a separate 1.116B-parameter target architecture. Its module explicitly says it has random weights and no trained checkpoint. The current `bozer_neural.train` trainer does **not** train this 1B architecture.

## Dataset currently in the repository

The French JSONL dataset is balanced across six categories:

| Split | Examples | Per category |
|---|---:|---:|
| Train | 204 | 34 |
| Validation | 48 | 8 |
| Test | 48 | 8 |

These examples are marked `original_synthetic`. They are a small smoke-start corpus, not enough to teach a useful general-purpose assistant. A real training run on them would only prove that the pipeline executes; it would not prove BOZER has learned broad knowledge or strong reasoning.

No trained checkpoint was found at `artifacts/bozer-neural-v0.4.pt` on this branch during this audit.

## Run the existing small-model training pipeline

From the repository root, with the project's Python dependencies installed:

```bash
python -m bozer_neural.validate_corpus
python -m bozer_neural.train --steps 600 --batch-size 8 --eval-interval 50
python -m bozer_neural.evaluate --checkpoint artifacts/bozer-neural-v0.4.pt --data data/bozer_neural_v0_4/test_fr.jsonl --report artifacts/bozer-neural-evaluation.json
```

Resume/fine-tune an existing compatible checkpoint:

```bash
python -m bozer_neural.train --resume artifacts/bozer-neural-v0.4.pt --steps 300 --output artifacts/bozer-neural-v0.4-resumed.pt
```

The trainer automatically selects CUDA when available, otherwise CPU. These commands are intentionally not run by this documentation change.

## Important separation

The current Android/Python dialogue engine uses a configured model provider (or an optional local llama.cpp endpoint). The small experimental `BozerTransformer` is not shown to be the model serving Android conversations. Training this experimental checkpoint will therefore not automatically change BOZER's live answers.

Before calling the brain trained or connecting its checkpoint to the app, require:
1. a larger, reviewed, rights-cleared training corpus;
2. a completed run with saved checkpoint and recorded step/loss metrics;
3. a held-out test report and qualitative review;
4. a runtime adapter that loads the exact checkpoint and tokenizer;
5. latency and memory benchmarks on the intended target device.

The 1B architecture needs its own memory-aware trainer/checkpoint path before it can be trained; do not pass it to the current small-model trainer.


## Separate 1B trainer (verified in repository)

The repository also contains `bozer_neural/train_1b.py`, a trainer specifically for `BozerNeural1B`. It is separate from the small-model trainer and supports:

- dialogue fine-tuning (loss only on BOZER answer tokens and EOS);
- plain-text next-token pretraining;
- separate validation data and train/validation leakage checks;
- BPE tokenizer saved inside the checkpoint, with reserved vocabulary rows masked;
- gradient accumulation, gradient checkpointing, CUDA mixed precision (auto/bf16/fp16), AdamW, warmup/cosine learning-rate schedule and early stopping;
- resumable versioned checkpoints and best-validation checkpoint;
- a hardware guard that refuses large CPU training by default and checks a lower-bound CUDA memory estimate.

The default 1B configuration has **1,115,785,216 parameters**. The trainer estimates a minimum of **17,852,563,456 bytes (about 16.6 GiB)** just for FP32 weights, gradients and AdamW moments, before activations, temporary buffers and runtime overhead. This is not a recommended GPU size; actual training needs more free memory. The full model should not be launched on an ordinary phone or a low-memory machine.

### Small pipeline smoke test

The 1B trainer has unit tests using a tiny configuration, including a one-step supervised run, a one-step plain-text pretraining run, and a resume from pretraining into dialogue fine-tuning. Those tests validate the training pipeline, not the quality of a trained 1B model.

### Full 1B commands (only after preparing a substantial licensed corpus)

Plain-text pretraining:
```bash
python -m bozer_neural.train_1b --data data/bozer_pretraining/train.txt --validation-data data/bozer_pretraining/validation.txt --output artifacts/bozer-neural-1b-pretrain.pt --steps 1000 --batch-size 1 --grad-accum-steps 16
```

Dialogue fine-tuning, resuming the pretraining checkpoint:
```bash
python -m bozer_neural.train_1b --data data/bozer_dialogue/train_fr.jsonl --validation-data data/bozer_dialogue/validation_fr.jsonl --output artifacts/bozer-neural-1b-dialogue.pt --resume artifacts/bozer-neural-1b-pretrain.pt --steps 500
```

Do not use the current 204/48/48 synthetic dialogue split as the final training corpus. It is useful for smoke tests and early pipeline checks only. Build and review a much larger corpus with documented redistribution/commercial rights, balanced domains, deduplication, and an untouched test split before a meaningful training run.

### Current integration boundary

Even a successfully trained 1B checkpoint will not automatically power Android chat. A separate loader/runtime adapter, model conversion/quantization plan, Android-compatible inference integration, and measured device performance are still required. No full 1B training run or production checkpoint is claimed by this document.
