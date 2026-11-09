# BOZER AI

BOZER is an Android assistant project focused on local conversation, explicit memory, transparent research and user-confirmed phone actions.

## Android application

The Android app is a native Kotlin/Jetpack Compose application with a JNI bridge to llama.cpp.

- Minimum Android version: API 26 (Android 8).
- Build ABIs: `arm64-v8a` and `x86_64`.
- Current app model: Qwen2.5-0.5B-Instruct Q4_K_M, downloaded on first use (491,400,032 bytes).
- The GGUF is downloaded into BOZER's private internal storage and checked against a pinned SHA-256 before it is loaded.
- The model is not bundled in the APK. The first installation therefore needs Internet access and roughly 600 MB of free internal storage. Later local inference does not need a model API key or a remote AI service.

Build the debug APK with:

```sh
gradle :app:assembleDebug --no-daemon
```

The installable debug APK is uploaded by the **BOZER Android APK build** GitHub Actions workflow as the `bozer-debug-apk` artifact. Debug APKs are test-signed, not Play Store release builds.

## Implemented Android capabilities

- One primary chat experience with a native local model.
- Multi-turn ChatML prompt construction, bounded history and explicit local facts.
- SQLite conversation history and explicit memory, with edit/forget/clear controls.
- Android-side Wikipedia search over a real HTTPS request. Search is opt-in through a command such as `Recherche sur Internet : capitale du Burkina Faso`. Returned snippets are treated as untrusted data and source URLs are displayed.
- Explicitly confirmed Android intents for opening installed apps, preparing alarms, opening the dialer and opening an SMS composer. BOZER does not directly place calls or send SMS.
- A user-enabled floating bubble with a foreground notification. It only opens BOZER and does not inspect other apps.
- Manual French press-to-talk using Android's on-device speech recognizer when the device provides one. Microphone permission is requested only after the user taps the microphone button; there is no cloud-recognition fallback.

## Deliberate limits

- The 0.5B local model is a small starter model. Its knowledge, reasoning and response speed are limited by the model and the phone's CPU/RAM; it is not equivalent to ChatGPT.
- The floating bubble is user-activated and can be stopped from its notification or BOZER settings. Android may still stop services under memory pressure; behavior varies by manufacturer.
- Manual voice input is not an always-listening "BOZER" wake word. No microphone is kept active in the background and BOZER does not automatically start listening after boot or while locked.
- Offline speech transcription depends on an on-device French recognition service being installed and available on the phone.
- The Android app does not require a paid AI API key and does not activate the Python remote-provider adapters. Those adapters are separate server-side foundations and are not called by the Android app.
- Physical-device validation is still required for OEM-specific overlay behavior, memory pressure, battery use, voice service availability and actual Android 13 performance.

## Privacy and safety

- No provider API key is embedded in the APK, app resources or source code.
- Conversation history and explicit facts are stored in the app's private SQLite database. Android app backup is disabled.
- Model inference runs locally after the GGUF download.
- Search sends the explicit search query to Wikipedia over HTTPS.
- Calls and SMS are never sent directly. The user must confirm the action and then complete it in Android's dialer or messaging application.
- Microphone access is requested only when the user taps the microphone control. No background recording is implemented.

## Python decision core

The portable Python package `bozer_brain` contains intent parsing, safe action proposals, task planning, SQLite memory, EYE_SEARCH adapters and model-gateway interfaces. Its tests run with:

```sh
python -m unittest discover -s tests -v
```

The Python provider adapters are not configured with credentials by default. A real remote provider may incur charges and must only be enabled through a trusted backend after explicit approval; no such key is required for the Android local-model path.

## Validation workflows

GitHub Actions includes:

- **BOZER core tests** — Python unit tests.
- **BOZER Android APK build** — native Android compilation and installable debug APK artifact.
- **BOZER Android emulator and real-brain tests** — installs the APK on an Android 13 emulator, installs and verifies the actual GGUF, runs instrumented UI/model/search/action tests, and uploads logs, memory diagnostics and a screenshot.

An emulator run does not prove behavior on a physical Android phone. Physical testing remains a separate validation step.
