# BOZER AI — embedded brain foundation

BOZER is being rebuilt around an **on-device language model bundled inside the Android APK**. The user installs one APK; the model is not downloaded by the app at first launch and no paid inference API is required for local chat.

## First embedded model

- Model: Qwen2.5-1.5B-Instruct, GGUF Q4_K_M
- Source: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
- License: Apache-2.0 (retain the upstream license and notices)
- Expected model file: about 1.12 GB
- Runtime: upstream llama.cpp Android example (Arm AI Chat binding)

The 1.5B model is a stronger starting point than a tiny 0.5B model and supports French among 29+ languages. It is still a compact on-device model, not equivalent to a frontier cloud assistant. French quality, latency, RAM use and thermal behaviour must be tested on real devices.

## One APK, model included

GitHub's normal Git file storage is not suitable for a ~1.12 GB model blob. Therefore the build workflow fetches the model **during APK compilation**, places it in the Android app's assets, and packages it into the APK. The installed application copies it into its private app storage on first launch and loads it locally. There is no separate model installer or first-run model download.

## Build

Push to `main` or `bozer-embedded-brain`, or run the **BOZER Android — embedded brain** workflow manually. The workflow:
1. Checks out this repository.
2. Clones the upstream llama.cpp Android sample.
3. Downloads the Qwen GGUF model into the APK assets.
4. Applies BOZER branding and automatic bundled-model loading.
5. Builds a debug APK and publishes it as a GitHub Actions artifact.

The first APK is a technical baseline, not a claim that all BOZER features or the final interface are complete.

## Device requirements

The current upstream Android binding requires Android 13 / API 33 or newer and supports ARM64 devices. The model is about 1.12 GB and needs additional RAM and free storage while the app copies it into private storage. Low-memory phones may be slow or may close the app; this must be checked on the target phone.

## Licensing

BOZER must retain the model's Apache-2.0 license and all required notices. llama.cpp and the Arm AI Chat Android binding have their own upstream licenses; keep their license files and notices when distributing the APK.
