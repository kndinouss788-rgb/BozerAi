# BOZER AI

BOZER is a privacy-conscious assistant project designed to turn natural-language intentions into safe, understandable actions.

## Repository foundation

This repository currently contains a portable Python decision core. It is not yet the Android application and does not execute phone actions by itself.

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

The Android UI, floating overlay, secure backend deployment, and real device execution remain separate work.

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
