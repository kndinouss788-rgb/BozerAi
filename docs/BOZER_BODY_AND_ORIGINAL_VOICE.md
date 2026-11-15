# BOZER body + BOZER original voice — implementation contract

Status: architecture specification; this document does not claim that a custom TTS model or Android integration has already been implemented.

## Product rule

BOZER NEURAL remains the permanent identity and decision core. The Android body is its trusted actuator and presentation layer. Speech synthesis is a presentation subsystem, not a second brain. A cloud voice provider must never silently replace BOZER's own voice or become a mandatory runtime dependency.

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

**Approved creative direction:** original masculine voice, French-first, with the polished, futuristic assistant feeling found in science-fiction films. The reference is the level of conversational sophistication and human-like naturalness—not a copy of JARVIS, an actor, a character, or any existing voice.

#### Voice identity

- Masculine adult voice; warm mid-to-low register, clear resonance, confident but never domineering.
- Natural French delivery, neutral and broadly understandable; avoid exaggerated regional accent.
- Human-like rhythm: varied intonation, subtle pauses, connected phrasing, natural sentence endings, and context-aware emphasis.
- Personality: calm intelligence, directness, attentiveness, restrained humor, and reassuring clarity when needed.
- Emotional range: neutral conversation, friendly acknowledgement, focused explanation, gentle warning, and brief excitement. No theatrical overacting.
- Short acknowledgement style: concise and natural, e.g. "Oui ?", "Je t'écoute.", "C'est fait." only when the action is actually verified.
- Never claim a task succeeded merely because speech was generated.

#### Audio character

- Clean studio-like sound, close and present, without artificial radio, metallic, vocoder, or heavy cinematic processing.
- Avoid extreme bass, exaggerated pitch shifting, robotic monotony, and excessive breath/noise.
- Prioritize intelligibility on inexpensive phone speakers and in noisy everyday environments.
- Keep speaking rate adjustable; default to a measured conversational pace, not a slow navigation voice.

#### Creation and training stages

1. Freeze the voice identity brief in `bozer_neural/voice/voice_profile.json`.
2. Obtain a lawful, consistent recording set from a consenting speaker or commissioned voice actor; retain written consent, permitted usage scope, and dataset provenance. Do not scrape voices or use unlicensed recordings.
3. Build aligned text/audio data, pronunciation lexicon, speaker consistency checks, and train/validation/test splits by recording session.
4. Compare compact open-weight TTS architectures and their licenses; do not adopt a hosted API as the permanent voice engine.
5. Train or fine-tune a BOZER-specific acoustic model and vocoder only when suitable data and compute exist. Save checkpoints and reproducible configs.
6. Export and optimize for Android (quantization, streaming chunks, low-latency startup, offline inference, memory and battery limits).
7. Evaluate intelligibility, naturalness, pronunciation, latency, stability, and speaker consistency on unseen French sentences before calling it BOZER's production voice.

### Runtime contract

The Android layer should depend on a small interface such as:

- `speak(text, utteranceId, options)`
- `stop(utteranceId?)`
- `isReady()`
- `release()`

The eventual implementation should stream audio in short chunks, support interruption, avoid overlapping utterances, respect audio focus, and work offline. A platform TTS adapter may be used temporarily for development tests only; it must be clearly marked as a temporary fallback and must not be presented as BOZER's finished voice.

## Acceptance gates

- Brain: held-out dialogue and reasoning evaluation passes; no claim of learning based solely on architecture tests.
- Body: emulator tests plus real-device tests for permission denial, background/foreground transitions, audio focus, interruption, and verified action results.
- Voice: blinded listening tests and objective intelligibility/latency checks on unseen French sentences; no production claim before a trained checkpoint is actually packaged and tested.
- Integration: the voice speaks only the final response selected by BOZER NEURAL; it cannot change policy, memory, permissions, or action decisions.

## Resource and honesty constraints

A from-scratch neural TTS voice requires a lawful, consistent speech dataset and compute. No custom voice model is trained by writing this specification. Do not spend money, upload private recordings, or connect paid providers without the owner's explicit approval. Prefer a compact offline model for the eventual Android target, but measure actual RAM, storage, latency, and battery use before choosing a model size.
