# BOZER TTS — runtime research and provisional decision

Date of review: 2026-11-15.

## Provisional runtime candidate: sherpa-onnx

The upstream sherpa-onnx project is Apache-2.0 licensed, includes local text-to-speech, documents Android support, and provides Kotlin/Java APIs. Its Android documentation includes both a standalone TTS example and a TTS engine example. This makes it a strong candidate for the Android runtime boundary.

Official references:
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/k2-fsa/sherpa-onnx/blob/master/android/README.md
- https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/android/build-sherpa-onnx.rst

No dependency has been added to the BOZER APK yet. Before integration, pin a release, inspect the AAR/native ABI contents and all transitive notices, check the exact minimum Android API, and measure package size, RAM, CPU latency and battery on representative devices.

## Model and voice weights are a separate license decision

A runtime's license does not automatically grant rights to its voice checkpoints or training data.

Piper's original source repository is MIT-licensed but was archived on 2025-10-06. Its published voice list includes French voices, but each checkpoint and its dataset require an independent commercial-use and redistribution review. BOZER will not package a voice merely because the inference runtime is permissively licensed.

Official references:
- https://github.com/rhasspy/piper
- https://github.com/rhasspy/piper/blob/master/LICENSE.md
- https://github.com/rhasspy/piper/blob/master/VOICES.md

HayaiTTS is a useful current Android implementation reference, but its own repository is GPL-3.0 and states that individual voice licenses vary. BOZER should not copy or bundle its GPL code without a separate compatibility review.

Reference:
- https://github.com/HayaiApp/HayaiTTS

## BOZER decision

- Treat sherpa-onnx as the first runtime candidate, not as BOZER's voice identity.
- Keep the final voice original: masculine adult French, warm mid-low timbre, polished futuristic assistant delivery, no JARVIS/actor/character imitation.
- Train or fine-tune only with a documented consenting speaker dataset and a model/checkpoint whose license allows BOZER's intended commercial distribution.
- Keep the model offline-capable, interruptible, streamable and independent of a paid hosted API.
- Do not choose, download or redistribute any third-party French voice until its exact checkpoint, dataset and license have been checked.
- Do not integrate any GPL-licensed third-party application code into the BOZER Android app without a deliberate legal/license decision.

This is a provisional engineering choice. It is not a claim that a custom BOZER TTS model exists or is ready.
