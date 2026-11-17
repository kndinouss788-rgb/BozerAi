# BOZER NEURAL — verified training status

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
