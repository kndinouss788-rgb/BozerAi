# BOZER AI — Android assistant

BOZER is an Android assistant project under active development. The repository now contains an installable Android application, a native on-device language-model runtime, a local conversation database, public web search, and guarded Android actions. It is **not yet declared complete**: emulator inference, device-specific performance, and physical-device behavior must still pass validation.

## Android application

- **UI:** one Kotlin + Jetpack Compose chat experience, designed around a light pearl/white surface.
- **Local brain:** llama.cpp is compiled through the Android NDK and called through JNI. The app downloads the pinned Qwen2.5-0.5B-Instruct Q4_K_M GGUF model on first installation; the roughly 491 MB model is not bundled in the APK.
- **Model integrity:** the downloaded file is checked against a pinned SHA-256 before being installed in BOZER's private internal storage. A verified local copy can then be loaded without downloading it again.
- **Memory:** conversation history and explicitly requested memories are stored in a local SQLite database. The app exposes controls to correct or forget individual memories and clear history or memories separately.
- **Internet research:** an explicit “Recherche sur Internet” request invokes the French Wikipedia search API from the Android process. Returned titles and snippets are treated as untrusted evidence and source links are shown in the answer. This is a real network search, but it is currently Wikipedia-only, not a general multi-engine web search.
- **Phone actions:** supported intents include opening an installed launcher app, opening the Clock app to prepare an alarm, opening the dialer, and opening an SMS composer. BOZER asks for confirmation first; calls are not placed and SMS messages are not sent automatically.
- **Voice:** speech input uses Android's `RecognizerIntent` after the runtime microphone permission is granted. Speech output uses Android TextToSpeech. Availability and language quality depend on the speech services installed on the device. There is no always-listening wake word yet.
- **Floating bubble:** a user-enabled overlay foreground service displays a movable shortcut that opens the main BOZER activity. It does not record audio or execute phone actions from the overlay.

## Privacy and model limits

- By default, chat inference runs locally after the model has been installed.
- The optional HTTPS BOZER gateway is disabled unless a gateway URL is explicitly configured at build time. No provider key is embedded in the Android app.
- Selecting online mode sends the conversation context needed for a response to the configured gateway. Do not configure a gateway unless its operator, privacy policy, and cost are understood.
- Search requests contact Wikipedia only when the user explicitly asks for a search and the device has validated Internet access.
- The bundled starter model has 0.5 billion parameters. It is a small local model, not a ChatGPT-equivalent model. Latency, answer quality, RAM, CPU use, heat, and battery impact must be measured on the target phone before making performance claims.

## Build and test

Requirements: JDK 17, Android SDK, Android NDK `27.2.12479018`, CMake `3.22.1`, and Gradle 8.9.

```bash
# Python decision-core tests
python -m unittest discover -s tests -v

# Installable debug APK (arm64-v8a and x86_64)
gradle :app:assembleDebug --no-daemon

# Instrumented tests on a running API 33+ emulator
gradle :app:connectedDebugAndroidTest --no-daemon
```

GitHub Actions runs the Python core tests, native Android APK build, and a dedicated emulator workflow. The emulator workflow downloads and verifies the real GGUF model, installs it into the app's private data directory, runs real local inference and Android network-search tests, and captures logs, a screenshot, memory and CPU diagnostics. It also force-stops and relaunches BOZER to test persistence.

The installable debug APK is published as the **`bozer-debug-apk`** artifact by the **BOZER Android APK build** workflow. Artifacts are retained for 14 days; download them from the matching successful run's **Actions → Artifacts** section. A debug APK is signed with the Android debug key and is intended for testing, not Play Store release.

## Current validation boundary

A successful compilation or Python test run alone does not prove the Android brain works. Before calling BOZER complete, the same `main` commit must pass all relevant workflows, including the real-model emulator dialogue, persistent memory/history, actual Android network search, guarded actions, and overlay tests. A physical Android 13 device is still required to validate device-specific performance, speech services, overlay behavior, and battery impact.
