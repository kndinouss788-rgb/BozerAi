# BOZER AI

BOZER is a privacy-conscious assistant project designed to turn natural-language intentions into safe, understandable actions.

## Repository foundation

This repository contains a portable Python decision core, an Android Kotlin/Compose application with native action bridges, and a Cloudflare gateway. The Android app is still under active integration and device validation; repository code or CI success does not by itself prove behavior on a user phone.

Core modules include intent parsing, safe action proposals, permission contracts, task planning, local SQLite memory, EYE_SEARCH (Wikipedia, Wikidata and Brave Web Search), and dialogue orchestration.

## Real language-model connection

BOZER now includes a native OpenAI Responses API adapter (POST /v1/responses) and a compatibility adapter for endpoints exposing /chat/completions. The native adapter sends store=false and parses assistant output text.

Configure these variables on a trusted backend/server, never in Android source, app resources, logs, or the APK:

- BOZER_MODEL_API_KEY: secret API key.
- BOZER_MODEL_NAME: exact model identifier enabled for that key.
- BOZER_MODEL_BASE_URL: optional; defaults to https://api.openai.com/v1.
- BOZER_MODEL_TIMEOUT: optional; defaults to 25 seconds.
- BOZER_MODEL_API_STYLE: optional; defaults to responses; use chat_completions for compatible third-party endpoints.

Use ModelGateway(openai_provider_from_env()) to configure the dialogue engine. Model calls still require allow_model=True; web research separately requires allow_search=True.

A real provider request can incur charges. The adapter is implemented but is not activated until the owner supplies a valid provider key and model name. Never commit a key. A production Android app must call an authenticated BOZER backend that protects the secret and applies consent and usage limits.

Phone-action intents remain proposals and bypass model generation; Android must independently validate permissions and confirmations.

Conversation history remains local unless the host explicitly sends prompt context to a remote model. Search results are untrusted evidence, not instructions.

The Android UI, floating overlay, secure backend deployment, and real-device behavior are being developed and validated in separate stages.

Run tests with: python -m unittest discover -s tests -v


## Zero-cost local conversation model

BOZER supports a local llama.cpp OpenAI-compatible endpoint with no API key:
- Download a compatible GGUF instruction model such as Qwen2.5-0.5B-Instruct Q4_K_M (about 491 MB) from its official Hugging Face model repository.
- Run llama.cpp locally on the device and bind its HTTP server to loopback only (127.0.0.1), not 0.0.0.0.
- Set BOZER_LOCAL_MODEL_URL=http://127.0.0.1:8080/v1 and optionally BOZER_LOCAL_MODEL_NAME=qwen-local.
- DialogueEngine.from_environment() prefers this local provider over a configured remote provider.
- Model calls still require allow_model=True. Local inference uses device CPU/RAM/battery and does not require paid API credits.

This repository currently adds the provider bridge and tests; it does not yet embed/build llama.cpp in the Android app or bundle/download a model automatically. Android JNI/runtime integration and a real-device inference test remain required. A 0.5B model is a modest starter, not ChatGPT-level intelligence.


## BOZER ACTION — Mission mode (execution core)

The persistent mission engine is available in `bozer_brain.mission`.

- Create a mission from an explicit objective and a dependency-ordered set of steps.
- Persist mission, step status, attempt counts, and event history in SQLite.
- Execute only tools explicitly registered by the trusted host.
- Require declared permissions and pause for human approval before sensitive steps.
- Mark a step successful only when its tool returns a verified result.
- Retry explicitly retryable failures within a per-step attempt limit.
- Stop at a step/time budget, cancellation request, missing tool, missing permission, or approval gate.
- Resume a `limit_reached` mission from its saved checkpoint by calling `run` again.

Minimal host integration:

```python
from bozer_brain.mission import MissionRunner, MissionStep, MissionStore, MissionTool, ToolResult

store = MissionStore("bozer_missions.sqlite3")
mission_id = store.create("Prepare a project report", [
    MissionStep("collect", "Collect project facts", "research", {}),
    MissionStep("report", "Write the report", "write_report", {}, ("collect",)),
])
runner = MissionRunner(store, {
    "research": MissionTool(research_handler),
    "write_report": MissionTool(report_handler),
})
report = runner.run(mission_id, max_steps=30, max_seconds=900)
```

This module is a tested execution core, **not yet a deployed overnight agent**. It does not invent a planner, run arbitrary shell commands, call a model, or provide Android/Cloudflare tool handlers by itself. A trusted host must supply the planner, registered handlers, persistent always-on runtime, authenticated mission API, and user-facing mission controls. GitHub Actions runners are temporary and are not a dependable all-night service. On Android, long missions require a visible foreground service and must respect OS battery/background limits. External messages, calls, purchases, posts, account changes, and destructive actions must pause for explicit user approval.


### Model-driven mission planning

`MissionPlanner` and `MissionService` connect the configured `ModelGateway` to the persistent runner:

- `create_from_objective(..., allow_model=True)` asks the configured model to produce a bounded JSON plan.
- The planner validates every step and rejects unknown tools, malformed arguments, invalid dependencies, cycles, and oversized plans.
- The model can select only names from the host-provided tool catalog. It cannot register a new tool, grant a permission, or execute an action.
- Sensitive tool names (messaging, calls, posting, payments, deletion, account changes, etc.) are forced behind a human approval gate.
- The service runs only the exact matching registered handlers; permissions are checked again by the runner.

Example:

```python
service = MissionService(
    store, model_gateway,
    tools=registered_handlers,
    tool_catalog=approved_tool_descriptions,
    granted_permissions=granted_permissions,
)
mission_id, plan = service.create_from_objective(
    "Research the project and prepare a progress report",
    allow_model=True,
)
report = service.run(mission_id, max_steps=20, max_seconds=600)
```

The host must still implement and register real handlers, expose authenticated mission controls, and provide an always-on runtime. The current repository integration is a Python service boundary; it is not yet connected to the Android UI or deployed as a persistent cloud worker.


## BOZER Android voice, alarms, and reminders

The Android application now contains an initial local-first foundation for BOZER-owned voice and alarm features:

- **Spoken replies:** optional French text-to-speech through Android's installed speech engine. This does not call a BOZER cloud voice API.
- **Voice input:** an in-app `SpeechRecognizer` controller replaces the separate system voice-activity screen and prefers Android's on-device recognizer when the device reports that one is available. If unavailable, Android may use its configured recognition service; fully offline speech recognition is not guaranteed.
- **Local alarms:** one-shot, daily, and weekday alarms are stored in the app's SQLite database and scheduled through Android `AlarmManager`; they do not require the chat model or network.
- **Snooze:** alarm notifications offer a separate 10-minute reminder without changing the original recurring schedule.
- **Notifications:** alarm notifications use a dedicated high-importance channel with alarm sound and vibration.
- **Recovery:** enabled future alarms are rescheduled after device reboot and after exact-alarm access is granted.
- **Commands:** examples include `mets une alarme à 7h30`, `réveille-moi à 6h`, `programme une alarme tous les jours à 6h`, `mets une alarme du lundi au vendredi à 7h30`, `rappelle-moi à 18h15 pour prendre le dossier`, `rappelle-moi dans 10 minutes d'appeler maman`, `quelles sont mes alarmes`, `annule la prochaine alarme`, and `annule toutes les alarmes BOZER`.

Android 12+ may require the user to grant BOZER exact-alarm access. Without it, BOZER uses Android's inexact idle-safe scheduling fallback and must warn that the alarm can be delayed. Android 13+ also requires notification permission for visible notifications. Device manufacturers can apply additional battery restrictions. These features require APK build validation and real-device tests; always-on wake-word listening and a full alarm-clock screen are not claimed as complete yet. Recurring alarm and snooze code has automated coverage pending the latest Android build and device validation.


## BOZER completion tracking

See [`BOZER_COMPLETION_PLAN.md`](BOZER_COMPLETION_PLAN.md) for the master feature inventory, acceptance criteria, known blockers, and execution order. Features are not considered complete until the required tests and device/backend validation are recorded.
