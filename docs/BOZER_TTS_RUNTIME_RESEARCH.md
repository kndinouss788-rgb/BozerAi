# BOZER TTS — runtime and completion gates

Date of review: 2026-11-16.

## Integrated offline French TTS path

The Android branch now includes a local voice path built from:

- Sherpa-ONNX Android runtime, Apache-2.0, fetched during Gradle build from the official v2.0.1 release and verified against its SHA-256.
- The French `fr_FR-mls-medium` Piper-derived ONNX model and `fr_FR-mls-medium.tokens.txt`, downloaded once from the XODalG Sherpa-converted voice mirror into BOZER's private app storage. The checkpoint metadata describes 125 speakers.
- A Kotlin model manager that downloads to temporary files, checks minimum file sizes, and only renames complete files into place.
- A Kotlin speech adapter that initializes the ONNX model locally, selects provisional speaker ID 55 after validating that the checkpoint exposes 125 speakers, generates speech at a restrained 0.97 speed, and plays mono float PCM through Android AudioTrack. The selected profile's gender and timbre still require listening confirmation.
- Existing Android system TTS remains as a fallback when the offline model is not installed or cannot initialize.

The model is not downloaded as part of the APK. The user installs it from BOZER settings; it is then kept in app-private storage for later offline use. The Sherpa AAR is downloaded by Gradle at build time and is checksum-pinned.

The app integrates and configures an existing pretrained voice. It does not fine-tune the weights, create a new speaker identity, or make the output indistinguishable from a human. Naturalness, pronunciation, CPU cost, RAM, latency, and playback stability still require listening and measurement on physical Android devices.

## Model attribution and licensing

The Piper French MLS model card identifies the Multilingual LibriSpeech (MLS) corpus, OpenSLR 94, and CC-BY 4.0, and says the model was trained from scratch. The source attribution is recorded in `docs/BOZER_TTS_ATTRIBUTION.md`.

The runtime and model weights have distinct licensing terms. Preserve the Sherpa-ONNX Apache-2.0 notice and the MLS CC-BY 4.0 attribution when distributing BOZER. The exact converted checkpoint should still be checked against its own repository card before a commercial release.

## Android system TTS fallback

The existing `BozerVoiceOutput` adapter uses speech assets installed by Android:

- It selects a French voice, preferring offline voices and then a voice whose Android metadata explicitly signals a masculine profile.
- It reports the selected voice name, network requirement, and whether masculine metadata is actually available.
- It chunks long answers and supports queue replacement, stop, and release.

This remains a fallback; the installed engine and voice assets are supplied by Android or the phone manufacturer.

## Original BOZER voice — still requires consented recordings

The desired original voice remains an adult masculine French voice: warm mid-low register, clear and present, conversational and expressive, with a restrained futuristic-assistant character. It must not imitate JARVIS, an actor, or a fictional character.

A genuinely original BOZER voice still requires:
1. An adult speaker who explicitly agrees to TTS training, commercial BOZER use, and derivative synthetic speech.
2. Clean, transcribed recordings with separate training and evaluation sessions.
3. A licensed fine-tuning/training path and a trained BOZER checkpoint.
4. Pronunciation and naturalness review on held-out recordings.
5. Measurements on supported phones for latency, RAM, battery, and offline stability.

## Release gate

Do not label BOZER TTS complete until:
- Gradle and Kotlin compilation succeed with the new Sherpa dependency.
- Android unit tests and APK assembly succeed.
- The local model downloads and initializes on a supported phone.
- The masculine profile is actually heard and verified by the project owner.
- French pronunciation, pauses, and long replies are tested in real use.
- The model, runtime, and attribution notices are included in the release package.

A successful CI build is not a substitute for listening to the voice on a physical phone.
