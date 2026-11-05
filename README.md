# BOZER AI

BOZER is a privacy-conscious Android assistant designed to turn natural-language intentions into safe, understandable actions.

## Repository foundation

This repository contains a portable Python decision core. It is **not yet the Android application** and does not execute phone actions by itself.

### Core modules

- `bozer_brain/models.py`: typed intent and action models.
- `bozer_brain/intent.py`: deterministic French/English intent extraction.
- `bozer_brain/planner.py`: converts intents into safe action proposals.
- `bozer_brain/safety.py`: confirmation and permission gates.
- `bozer_brain/memory.py`: local SQLite conversation history and explicit key/value facts.
- `bozer_brain/dialogue.py`: conversation orchestration and optional injected model-responder boundary.
- `tests/`: automated tests.

## Memory and privacy

The application chooses the SQLite database path. Conversation history and explicitly stored facts remain in that local database; this module does not upload them. Facts are stored only when the caller explicitly invokes `remember_fact`. The API supports reading, updating, and forgetting a fact, plus clearing conversation history.

## Model integration status

The dialogue engine accepts an injected responder, but no AI provider is connected by default. Without one, BOZER states this limitation honestly rather than inventing answers. Provider credentials, network calls, retention rules, and user consent must be implemented in a separate integration layer.

## Safety boundary

This core never sends messages, places calls, launches apps, or creates alarms. It only returns proposals. Android must check runtime permissions, validate targets, obtain confirmation for sensitive actions, and report execution results truthfully.

Unknown or ambiguous requests stay in chat and are never converted into guessed actions.

## Run tests

```bash
python -m unittest discover -s tests -v
```

## Current status

Decision core, local memory, and dialogue orchestration foundation. Android UI, floating overlay, connected language-model provider, persistent Android integration, and real device execution remain separate integration work.
