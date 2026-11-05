# BOZER AI

BOZER is a privacy-conscious assistant project designed to turn natural-language intentions into safe, understandable actions.

## Repository foundation

This repository currently contains a portable Python decision core. It is **not yet the Android application** and does not execute phone actions by itself.

### Core modules

- `bozer_brain/models.py`: typed intent and action models.
- `bozer_brain/intent.py`: deterministic French/English intent extraction.
- `bozer_brain/planner.py`: converts intents into safe action proposals.
- `bozer_brain/safety.py`: confirmation and permission gates.
- `bozer_brain/tools.py`: explicit tool contracts and authorization checks.
- `bozer_brain/task_planner.py`: dependency validation, topological ordering, and per-step authorization.
- `bozer_brain/memory.py`: local SQLite conversation history and explicitly stored facts.
- `bozer_brain/context.py`: bounded recent-history context for a model integration.
- `bozer_brain/model_gateway.py`: provider-neutral model contract, explicit opt-in, response validation, and safe errors.
- `bozer_brain/dialogue.py`: conversation orchestration and optional injected responder.
- `tests/`: automated tests.

## Privacy and model integration

The application chooses the SQLite database path. History stays in that local database unless a separate integration sends context elsewhere. The context builder sends only a bounded number of recent turns to an injected responder. The model gateway refuses provider calls unless explicitly enabled. No AI provider or API key is bundled by default. Provider credentials, network calls, retention rules, and user consent must be implemented separately.

## Planning and execution boundary

The task planner validates dependencies and orders steps. Each step must pass the tool registry's argument, permission, and confirmation gates. A ready step is only authorized for a future Android adapter; it is **not executed** by this Python core. Android must independently validate permissions, targets, confirmation, and execution results.

Unknown or ambiguous requests stay unplanned rather than being converted into guessed actions.

## Run tests

```bash
python -m unittest discover -s tests -v
```

## Current status

Decision core, local memory, bounded conversation context, tool authorization, dependency-aware task planning, and a provider-neutral model gateway are implemented as foundations. Android UI, floating overlay, connected language-model provider, persistent Android integration, and real device execution remain separate work.
