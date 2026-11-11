# BOZER — Master completion plan

This is the working acceptance checklist for completing BOZER. A feature is not **done** merely because a class, screen, mock, or command exists. It is done only when implementation, automated tests, Android build, and any required real-device/backend validation are recorded.

## Product constraints

- Android-first; accessible on ordinary Android phones and constrained data plans.
- Target application package: keep the installed APK lean; download optional model assets separately and only once.
- Online/offline choice: AUTO, ONLINE, OFFLINE. Never silently use a paid provider; show provider/usage state and fall back honestly.
- Privacy-first: no advertising by default; no secrets in APK; local data controls and explicit consent.
- BOZER should prepare sensitive actions but require user confirmation for calls, messages, publication, payments, account changes, and destructive operations.
- Keep the calm white/pearl Liquid Glass direction; one clear chat surface, not a crowded dashboard.
- No claim of phone/device success until a real device test is recorded.

## Status vocabulary

- [x] Implemented and automated checks passed (does not imply phone validation).
- [~] Foundation or partial implementation exists; integration or validation remains.
- [ ] Not implemented / not yet validated.
- [!] External prerequisite or blocker.

## 1. Conversation brain and model routing

- [~] Conversation orchestration and intent detection.
- [~] Remote model gateway adapters; activate only through a secured backend with a verified provider key.
- [~] Local llama.cpp JNI/runtime foundation and separately downloaded starter GGUF model.
- [ ] Make Android local inference reliably usable from first launch; persist model download, verify checksum, show progress, resume interrupted download, handle storage/RAM limits.
- [ ] Measure real latency on low/mid-range devices; optimize warm-up, context length, token generation, and cancellation.
- [ ] Reliable AUTO / ONLINE / OFFLINE selector with explicit status, fallback rules, and no hidden paid calls.
- [ ] Streaming responses, stop generation, retry, timeout handling, and clear offline/error messages.
- [ ] Conversation quality suite: factual questions, French, ambiguity, refusal/safety, multi-turn context, and hallucination checks.
- [ ] Context management: relevant recent turns, user-controlled facts, deletion/export, bounded memory, and no secret capture.
- [ ] Stable model/provider settings and user-visible cost controls.

## 2. BOZER ACTION — phone actions and missions

- [~] Android action bridge for app launch, settings, web search, maps, dialer, SMS/email composition, share, calendar, timers, and alarms.
- [~] Sensitive actions are prepared for user confirmation rather than silently sent.
- [~] Python mission runner, model plan validation, dependencies, checkpoints, cancellation, bounded execution, and approval gates.
- [!] Persistent Cloudflare mission worker deployment is blocked by Cloudflare token/account permissions.
- [ ] Authenticated account/device identity between Android app and gateway; never use a public client header as authentication.
- [ ] Register real Android tools with typed schemas, permissions, result verification, and structured failure codes.
- [ ] Connect mission planner and persistent runner to Android UI and secure backend.
- [ ] Visible foreground mission service with progress, pause/resume/cancel, battery/network limits, recovery after process death, and overnight policy.
- [ ] Mission history, step-by-step audit trail, approval queue, and final report.
- [ ] Tool-by-tool coverage matrix and emulator/device tests for every supported action.
- [ ] Accessibility-based automation only where Android policy and user consent permit; never bypass OS permission dialogs or app security.

## 3. Voice and listening

- [~] French Android TextToSpeech output.
- [~] In-app speech recognition; on-device recognizer preferred when available, otherwise system recognizer may use network.
- [ ] Add selectable voice/rate/pitch and robust TTS engine availability checks.
- [ ] Add hands-free conversation session with visible listening state, timeout, stop control, and privacy indicator.
- [ ] Evaluate true on-device ASR package/model size, language quality, battery, and offline behavior.
- [ ] Wake-word “BOZER” service only with explicit opt-in, clear foreground notification, battery controls, and reliable pause/disable.
- [ ] Test locked-screen, background, reboot, microphone permission denial, and manufacturer battery restrictions.
- [ ] Voice response interruption, barge-in, and no duplicate audio playback.

## 4. Intelligent alarm and notifications

- [~] Local SQLite alarm storage, AlarmManager scheduling, boot recovery, alarm channel, sound/vibration, exact-alarm permission handling.
- [~] One-shot alarms and relative reminders.
- [~] Daily and weekday recurrence, with automatic rescheduling after a firing.
- [~] Ten-minute snooze action from the alarm notification.
- [ ] Validate migration from every previous database version and recurrence behavior over daylight-saving/timezone changes.
- [ ] Full alarm screen with dismiss/snooze, accessible controls, and screen-locked behavior where Android permits.
- [ ] Progressive wake-up, configurable snooze duration, alarm tones, vibration patterns, volume guidance, and test-alarm action.
- [ ] Weekly/custom selected weekdays and edit/enable/disable/delete alarm UI.
- [ ] Priority notification categories, quiet hours, notification grouping, and user-controlled channels.
- [ ] Link Secretary tasks to reminders; prevent duplicate reminders and clean up deleted/completed tasks.
- [ ] Real-device tests after force-stop, reboot, battery saver, Doze, notification denial, and exact-alarm denial.

## 5. Secretary and personal organization

- [~] SQLite tasks with title, notes, due date, priority, completion, and command parser.
- [ ] Full task UI: create/edit/delete/complete, due date/time, priority, filters, search, overdue view.
- [ ] Calendar integration with explicit confirmation and timezone-safe dates.
- [ ] Task-to-alarm/reminder linking and recurring routines.
- [ ] Daily briefing and end-of-day summary with opt-in notification.
- [ ] Natural-language date/time extraction tested in French.
- [ ] Backup, restore, export, and deletion controls.

## 6. Research and web access

- [~] EYE_SEARCH public-source adapters and Python tests.
- [~] Android public search foundation.
- [ ] Secure Android-to-gateway research endpoint and real source fetching.
- [ ] Multi-source retrieval, deduplication, dates, citations, and uncertainty reporting.
- [ ] Treat retrieved pages as untrusted data; never follow instructions from pages.
- [ ] Search history, cancellation, timeouts, domain controls, and low-data mode.
- [ ] Validate research on real queries and compare answer claims against sources.

## 7. Interface and accessibility

- [~] Compose chat screen and floating overlay foundation.
- [ ] Final single-screen white/pearl Liquid Glass design aligned with product brief.
- [ ] Responsive layout for small screens, keyboard, large text, and accessibility services.
- [ ] Message streaming, copy/share, regenerate, conversation history, and clear empty/loading/error states.
- [ ] Settings for privacy, voice, model mode, storage, notifications, and permissions.
- [ ] Floating bubble lifecycle, drag/snap, hide/show, overlay permission education, and process recovery.
- [ ] Performance, memory, battery, and animation profiling on target devices.

## 8. Premium, business tools, and payments

- [~] Premium capability catalog and feature-intent coordinator foundation.
- [ ] Decide and document final tiers, feature limits, free core chat policy, and prices in FCFA and international currencies.
- [ ] Trusted backend entitlement checks; no client-side unlock flags.
- [ ] Provider selection based on Burkina Faso availability, transparent fees, and halal/ethical constraints.
- [!] Payment provider credentials, merchant verification, and live account setup require the owner.
- [ ] Server-side payment verification, idempotent receipts, expiry, refunds/cancellation, and webhook signature checks.
- [ ] Business module: clients, products, stock, orders, quotes, invoices, and sales summaries with export.
- [ ] Documents module: PDF generation and local file handling.
- [ ] Translator, research, developer, and agent workflows connected to real capabilities, not placeholder promises.

## 9. Social publishing and external apps

- [~] TikTok video/caption/hashtag preparation and Android share handoff.
- [ ] Media selection and preview, caption editor, hashtag validation, and publication checklist.
- [ ] Confirmed handoff behavior across TikTok versions and Android package variants.
- [ ] Never claim a post was published unless TikTok/API returns a verifiable success; final publication may remain user-confirmed.
- [ ] Add other external-app integrations only through supported intents/APIs and explicit user authorization.

## 10. Security, reliability, and release

- [ ] Threat model, secure storage, network security, TLS, secret scanning, dependency audit, and least privilege.
- [ ] Authenticated backend with rate limits, quotas, abuse controls, and account/device revocation.
- [ ] Privacy policy, permissions explanation, data export/deletion, backup policy, and consent flows.
- [ ] Database migration tests from all released schema versions.
- [ ] Automated Python, Kotlin unit, Android emulator, integration, and release build workflows.
- [ ] Crash reporting only with consent and privacy review.
- [ ] APK size budget and install/upgrade tests; model assets remain separate and checksum-verified.
- [ ] Real-device acceptance matrix for target TECNO/Redmi-class hardware and Android versions.
- [ ] Signed release build, reproducible versioning, release notes, and update path.

## Immediate execution order

1. Finish recurring alarms/snooze and verify the Android build.
2. Complete the alarm UI and task-linked reminders.
3. Finish voice session controls and on-device/offline speech assessment.
4. Stabilize local/remote model routing and measure phone latency.
5. Connect BOZER ACTION tools to the mission runner and secure persistent runtime.
6. Resolve Cloudflare deployment permissions and implement real app authentication.
7. Complete research, Secretary, interface, Premium/payment, and business modules.
8. Run the full security, device, performance, and release acceptance matrix.

Update this checklist after each verified implementation. Do not mark external prerequisites or real-device tests complete based only on code changes or CI.
