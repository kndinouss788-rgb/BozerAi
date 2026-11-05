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
- `bozer_brain/eye_search.py`: bounded multi-source research orchestration, URL deduplication, sanitized errors, and untrusted-source context.
- `bozer_brain/dialogue.py`: conversation orchestration and optional injected responder.
- `tests/`: automated tests.

## EYE_SEARCH

EYE_SEARCH is a research layer for the BOZER brain, not a replacement for its reasoning. It accepts injected public-search adapters, can query at most three configured sources by default, deduplicates URLs, bounds result count and text size, and builds a temporary research context. External page text is explicitly labelled as untrusted data and must never be followed as instructions.

Search is disabled unless the caller explicitly passes `enabled=True`. No network adapter, crawler, API key, or paid search service is bundled yet. This keeps the core testable and prevents unexpected external requests; concrete public-source adapters and their privacy/availability rules are a separate integration step.

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

Decision core, local memory, bounded conversation context, tool authorization, dependency-aware task planning, provider-neutral model gateway, and the EYE_SEARCH orchestration framework are implemented as foundations. Android UI, floating overlay, connected language-model provider, real public-search adapters, persistent Android integration, and real device execution remain separate work.
