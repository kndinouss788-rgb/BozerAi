# BOZER platform roadmap — from Android app to native phone assistant

## Product target

BOZER should become a voice-first phone companion that can listen for an explicitly configured wake phrase, answer naturally, inspect supported device state, plan tasks, and perform approved actions. The Android app is the first distribution vehicle; OEM/system integration is a later deployment target.

## Safety and engineering boundaries

- Never hide Android's microphone privacy indicator or falsify microphone state.
- Do not continuously record or upload ambient audio. Wake-word detection should be local; send audio onward only after activation and with clear user consent.
- A spoken BOZER confirmation is not a substitute for Android runtime permissions, role selection, secure settings, or OEM restrictions.
- Sensitive actions must use an explicit action policy and an approval step. Report only actions that Android actually accepted or completed.
- Keep the approved Compose interface unchanged while platform work is developed.
- Keep the conversational core isolated: ChatRepository, RemoteBrainClient, prompts, model/provider configuration, and conversation-memory flow are not to be changed as part of platform scaffolding.

## What this repository has today

- A normal Android application (minSdk 26, targetSdk 35).
- A declared RECORD_AUDIO permission, but no declared VoiceInteractionService.
- A launcher Activity and existing text-to-speech output.
- A limited action planner for opening apps, preparing alarms, opening the dialer, and preparing SMS drafts.
- A remote-first/local-fallback conversation foundation.
- No production wake-word engine, no low-power hotword DSP integration, no system VoiceInteractionService, no continuous device-health monitor, and no OEM/system image integration.

## Implemented in this branch

### Read-only platform readiness probe

`BozerPlatformReadinessProbe` inspects, without changing device settings:
- microphone hardware and RECORD_AUDIO grant;
- whether Android exposes the assistant role;
- whether the app has actually declared a VoiceInteractionService;
- whether BOZER currently holds the assistant role;
- notification permission state on Android 13+;
- battery-optimization exemption state.

The probe is diagnostic scaffolding only. It does not request permissions, activate the microphone, enable a wake word, or make BOZER a privileged app. The current manifest does not declare a VoiceInteractionService, so the probe must not report that assistant setup is ready.

## Delivery phases

### Phase A — robust ordinary Android app (current affordable target)
1. Stabilize crash-free send, lifecycle, permissions, voice input/output, and action confirmation.
2. Add a capability dashboard/logging path that explains exactly which device features are available.
3. Implement safe action contracts: validate, preview, ask approval, execute, verify result, and report failure.
4. Add device-health checks using public Android APIs only (storage, battery state, app version, connectivity); do not claim the app can inspect every other app or repair Android automatically.
5. Add tests for every action and permission-denied path.

### Phase B — user-selected Android assistant
1. Implement a real `VoiceInteractionService`, its required metadata/configuration, and the required session/recognition lifecycle.
2. Offer Android's assistant-role selection flow; the user must choose BOZER in system UI.
3. Verify behavior on each supported OEM and Android version, including lock screen, screen-off, Doze, reboot, battery saver, and Bluetooth/headset routing.
4. Do not claim wake-word support merely because the assistant role is held. The role and hotword detector are separate capabilities.

### Phase C — local wake-word engine
1. Select a licensed, small-footprint wake-word engine and verify language/accent performance for the target market.
2. Keep detection local and minimize buffered audio; discard non-trigger audio.
3. Benchmark false accepts, missed detections, battery drain, RAM, thermal impact, and latency on low-cost phones.
4. Use a hardware Sound Trigger / DSP path only when the OEM firmware exposes a compatible HAL/model path. Otherwise evaluate a software foreground-service fallback with clear Android privacy indicators and battery cost.
5. Provide a visible opt-in, mute control, sensitivity setting, and clear microphone status. Never promise invisible microphone use.

### Phase D — device management and missions
1. Build an allow-listed device capability layer for supported Android intents/APIs.
2. Add a mission planner that decomposes a request into previewable steps.
3. Require approval for calls, messages, purchases, deletion, sharing, account/security changes, and other consequential actions.
4. Verify each step and stop safely when permission, network, or app state blocks it.
5. Use AccessibilityService only for user-authorized assistive workflows where appropriate; never as a way to bypass security dialogs or user consent.

### Phase E — OEM / BOZER OS partnership
1. Prepare a partner demo, privacy/security architecture, battery measurements, compatibility matrix, and support plan.
2. Work with an OEM to preinstall BOZER and grant only the platform permissions required for specific functions.
3. For privileged permissions, system allowlists, default assistant integration, Sound Trigger HAL access, or boot-image changes, the OEM/system-image maintainer must integrate and sign the components.
4. Consider an AOSP-based BOZER device only when engineering, maintenance, certification, security updates, and distribution resources exist.

## Activation gates

Future modules must remain disabled until all prerequisites are verified:
- `SYSTEM_ASSISTANT`: real VoiceInteractionService declared, role available, and user-selected role confirmed.
- `LOCAL_WAKE_WORD`: licensed detector integrated, explicit opt-in, tested audio lifecycle, and measured battery/privacy behavior.
- `DSP_HOTWORD`: compatible OEM Sound Trigger HAL and model support confirmed on the target device.
- `DEVICE_HEALTH_MONITOR`: each metric mapped to a public API, tested on target Android versions, and accompanied by truthful limits.
- `OEM_PRIVILEGED_MODE`: written OEM integration agreement plus signed system image/allowlist.

## Immediate next engineering tasks

1. Wait for and inspect the current Android emulator workflow; fix only reproducible failures.
2. Add unit/instrumentation tests for the readiness probe and permission-denied behavior.
3. Integrate the probe into a developer diagnostic path without changing the approved chat UI.
4. Audit action execution and confirmation boundaries before adding more device controls.
5. Build and test a real voice capture lifecycle in the foreground first; only then design the assistant/hotword service.
