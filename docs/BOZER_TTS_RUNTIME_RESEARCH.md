# BOZER TTS — runtime decision and completion gates

Date of review: 2026-11-15.

## Implemented Android fallback

The Android app now has a more complete provisional speech-output path using the device's installed `TextToSpeech` engine:

- French voice selection, prioritizing offline voices and then voices whose Android metadata explicitly signals a masculine profile.
- Transparent status text: selected voice name, whether it needs a network connection, and whether masculine metadata is actually available.
- Speech-rate and restrained pitch preferences; pitch is not treated as evidence of gender.
- Long answers are split into bounded utterances and queued in order instead of being silently truncated.
- New speech replaces the previous queue, and stop/release cancel active or pending output.
- Settings expose Android's voice-data installation/management screen.

This is a working fallback layer, not a BOZER voice model. The installed speech engine and audio assets remain supplied by Android or the phone manufacturer. Availability, pronunciation, voice gender, quality and offline behavior vary by device and installed data.

## Candidate local runtime: sherpa-onnx

The upstream sherpa-onnx project is Apache-2.0 licensed, includes local text-to-speech, documents Android support, and provides Kotlin/Java APIs. It remains a candidate for a later self-contained BOZER runtime, not a dependency in the current APK.

Official references:
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/k2-fsa/sherpa-onnx/blob/master/android/README.md
- https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/android/build-sherpa-onnx.rst

A runtime's license does not automatically grant rights to its voice checkpoints or training data. Each candidate checkpoint needs its own review for commercial use, modification, and redistribution. Some French model cards use CC-BY datasets or disclose fine-tuning from unrelated base voices; these facts must be resolved for the exact weights before BOZER packages them.

## Original BOZER voice — still blocked by source material

The intended voice remains an original adult masculine French voice: warm mid-low register, clear and present, conversational and expressive, with restrained futuristic-assistant character. It must not imitate JARVIS, an actor, or a fictional character.

The repository has a voice design profile and a dataset validator, but **contains no consenting speaker recordings and no trained BOZER TTS checkpoint**. This prevents an honest claim that the original voice is finished. Required external inputs:

1. A consistent adult speaker who explicitly agrees to TTS training, commercial BOZER use, and derivative synthetic speech.
2. Recorded French speech with exact transcripts, clean WAV audio, and separate train/validation/test recording sessions.
3. Pronunciation review and evaluation on held-out utterances.
4. A licensed training/inference route and a trained checkpoint.
5. Device measurements for naturalness, intelligibility, latency, RAM, battery, and offline use.

The existing `validate_voice_corpus.py` checks the manifest and WAV format; it cannot create recordings, authenticate consent, train a speaker, or certify naturalness.

## Release rule

Do not label BOZER TTS "complete" until an original checkpoint exists, the dataset rights are documented, tests pass, and actual Android playback has been heard and measured. Until then the Android adapter must be labelled as a provisional system voice.
