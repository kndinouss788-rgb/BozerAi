# BOZER AI

**« Transformer l'intention en action. »**

BOZER AI is a native Android personal assistant being built around one conversational brain, user-controlled memory, voice, web research and guarded Android tools. Its personality is natural, warm, direct and honest. This repository is an active construction branch; it is not yet the finished product.

## Product rules

- A successful APK build is not proof that the brain, voice, search or Android actions work.
- BOZER must never say an action succeeded unless the responsible Android/API operation returned a success result.
- Simple conversation defaults to the local brain. The optional remote gateway is used only when the user explicitly selects **En ligne**.
- Long-term memories require an explicit request and remain separately manageable from chat history.
- Sensitive actions require a preview and confirmation. Calls open the dialer; SMS opens a draft and is never silently sent.
- Microphone access is requested when voice input is used. BOZER does not continuously listen for a wake word.
- No provider API key is packaged in the APK.
- Model output is a compact local model, not a claim of ChatGPT-level intelligence.

## Android foundation

- Kotlin + Jetpack Compose, Android app ID `ai.bozer.app`.
- llama.cpp native inference through JNI, with the NDK/CMake toolchain pinned in Gradle.
- SQLite chat history and explicit-memory storage.
- Local-first brain router (Automatique / Hors ligne) and optional HTTPS gateway mode (En ligne).
- Android TextToSpeech output and Android speech recognition input.
- Guarded intents for launching apps, preparing alarms in Clock, opening the dialer and composing SMS.
- An opt-in floating bubble that opens BOZER; no microphone or action execution from the overlay.

## Embedded local model

The planned starter model is **Qwen2.5-1.5B-Instruct GGUF Q4_K_M** (approximately 1.1 GB), distributed under its upstream Apache-2.0 license.

The model binary is intentionally not committed to Git. The manual packaging workflow downloads the pinned model source during APK preparation, records its exact byte count and SHA-256 in `bozer-model.sha256`, and adds the model plus license to the Android assets. BOZER copies the embedded asset once into its private app storage, verifies its checksum, and loads it with llama.cpp.

**One APK is intended to contain both the app and its brain.** The installed app should not download a separate model on first launch. Expect the APK to be large, and expect additional internal storage while the bundled model is copied to the app's private directory. No network connection is needed for that model-copy step or for local inference.

The model's French answer quality, first-load time, response latency, RAM use, heat and battery impact are not yet established on target phones. A 1.5B quantized model is a practical local starting point, not a frontier cloud model.

## Current functional boundaries

| Area | Present in recovered Android source | Remaining construction |
|---|---|---|
| Conversation | Chat screen, multi-turn local prompt, local/remote routing | Stabilize inference, context window, greeting quality, failure recovery and long-conversation handling |
| Memory | SQLite history, explicit remembered facts, edit/forget/clear controls, relevance selection | Better semantic retrieval, export, stronger deletion/retention controls |
| Voice | Android speech recognition request and French TTS | Improve user-visible states, interruption, precise text/audio synchronization and device fallback handling |
| Phone actions | Confirmed intents for app launch, Clock alarm preparation, dialer and SMS draft | Broader natural-language understanding, action journal and richer result verification |
| Research | Explicit Wikipedia search with links | Multi-source retrieval, source dates, robust extraction and source-quality handling |
| Terminal and settings | Existing composer shortcuts and basic preferences | Dedicated double-tap Terminal and a proper full-screen settings area |
| Seven modules | Design target only | BUSINESS, AGENT, RESEARCH, DOCUMENTS, SECRETARY, TRANSLATOR and DEVELOPER, implemented one by one |
| Visual identity | Pearl/silver Compose surfaces and orb foundation | Finish white liquid-glass treatment, adaptive animations and final information architecture |

The seven modules are not advertised as complete while their tools and acceptance criteria remain unfinished.

## Model preparation and packaging

Requirements for the eventual manual packaging workflow: JDK 17, Android SDK, NDK `27.2.12479018`, CMake `3.22.1`, and Gradle 8.9.

Prepare the model assets locally or in the controlled packaging runner:

```bash
scripts/prepare_bundled_model.sh app/src/main/assets
```

The final Android package job is deliberately **manual-only**. The APK build and emulator-validation workflows have also been changed to manual dispatch, so editing this construction branch does not automatically compile an APK. Do not dispatch them until the final product implementation is ready.

## Privacy and external services

- The local-first modes keep conversation inference on the phone.
- In **En ligne** mode, the chat context needed for a reply is sent to the configured BOZER HTTPS gateway. Its provider, policy, reliability and costs must be reviewed before enabling it for users.
- Search is only called after an explicit search request. Search results are untrusted data, not assistant instructions.
- The current Android search connector is Wikipedia-only; a general web search is not yet complete.
- Microphone permission is requested only when the user asks to speak. The current speech provider may apply its own network processing; BOZER requests offline recognition where available but cannot force the provider to honor it.
- No always-on wake-word listener is implemented.
- Floating overlay access is optional and user-controlled.
- Payment, subscription, ads and paid-provider routing are not represented as active product features.

## Progress tracking

See [`docs/BOZER_PROGRESS.md`](docs/BOZER_PROGRESS.md) for the current implementation percentage, phase status, code changes and unresolved work. Progress percentages describe implementation work in the repository, not real-device certification.
