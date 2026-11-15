# BOZER body + BOZER original voice — implementation contract

Status: architecture specification; this document does not claim that a custom TTS model or Android integration has already been implemented.

## Product rule

BOZER NEURAL remains the permanent identity and decision core. The Android body is its trusted actuator and presentation layer. Speech synthesis is a replaceable output subsystem, not a second brain. A cloud voice provider must never silently replace BOZER's own voice or become a mandatory runtime dependency.

## Parallel workstreams

### A. BOZER NEURAL (brain)

- Continue the from-scratch model, tokenizer, corpus provenance, training, and held-out evaluation work.
- Keep the current 1.116B-parameter configuration clearly labeled as an untrained architecture until a real checkpoint exists.
- Improve data quality and source-document-level train/validation/test separation before scaling training.
- Keep memory, policy, permissions, and action verification owned by BOZER's core.

### B. BOZER Android (body)

- Android owns microphone capture permissions, audio focus, lifecycle, foreground-service constraints, and user-visible listening state.
- The body sends recognized text and device context to the BOZER core through a narrow typed interface.
- The body executes only structured, permission-checked action proposals. It must never execute arbitrary model-generated shell/code.
- Every sensitive action keeps its confirmation gate; execution results are returned to the brain for verification.
- UI changes are out of scope unless separately requested.

### C. BOZER VOICE (original TTS)

Goal: one recognizable, original BOZER voice, French-first, warm, clear, youthful-adult, calm, natural, and direct. It must not imitate or clone a real person's voice without explicit documented consent.

Proposed stages:

1. Voice identity brief: settle pitch range, timbre, accent, speaking rate, emotional range, pronunciation rules, and the short wake/acknowledgement style.
2. Lawful recording set: obtain recordings from a consenting speaker or commission an original voice actor; retain consent, usage scope, and dataset provenance. Do not scrape voices or use unlicensed recordings.
3. Text/audio dataset: clean transcripts, sentence alignment, speaker consistency, pronunciation lexicon, train/validation/test split by recording session, and quality checks.
4. Baseline TTS research: compare small open-weight architectures and licenses; do not adopt a hosted API as the permanent voice engine.
5. Train/fine-tune a BOZER-specific acoustic model and vocoder only when a suitable dataset and compute are available. Save checkpoints and reproducible configs.
6. Export and optimize for Android (quantization, streaming chunks, low-latency startup, offline inference, memory/battery limits).
7. Evaluate intelligibility, naturalness, pronunciation, latency, stability, and speaker consistency on unseen sentences before calling it BOZER's production voice.

### Runtime contract

The Android layer should depend on a small interface such as:

- speak(text, utteranceId, options)
- stop(utteranceId?)
- isReady()
- release()

The eventual implementation should stream audio in short chunks, support interruption, avoid overlapping utterances, respect audio focus, and work offline. A platform TTS adapter may be used temporarily for development tests only; it must be clearly marked as a temporary fallback and must not be presented as BOZER's finished voice.

## Acceptance gates

- Brain: held-out dialogue and reasoning evaluation passes; no claim of learning based solely on architecture tests.
- Body: emulator tests plus real-device tests for permission denial, background/foreground transitions, audio focus, interruption, and verified action results.
- Voice: blinded listening tests and objective intelligibility/latency checks on unseen French sentences; no production claim before a trained checkpoint is actually packaged and tested.
- Integration: the voice speaks only the final response selected by BOZER NEURAL; it cannot change policy, memory, permissions, or action decisions.

## Resource and honesty constraints

A from-scratch neural TTS voice requires a lawful, consistent speech dataset and compute. No custom voice model is trained by writing this specification. Do not spend money, upload private recordings, or connect paid providers without the owner's explicit approval. Prefer a compact offline model for the eventual Android target, but measure actual RAM, storage, latency, and battery use before choosing a model size.
