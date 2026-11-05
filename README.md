# BOZER AI

BOZER is a privacy-conscious Android assistant designed to turn natural-language intentions into safe, understandable actions.

## Current repository scope

This repository now contains the first portable **decision-core foundation** for BOZER. It is not yet the Android application and does not execute phone actions by itself.

### Core pipeline

1. Parse a user's request into a typed intent.
2. Build a proposed action with explicit arguments.
3. Apply a safety policy and identify whether confirmation is required.
4. Leave execution to a future Android ActionExecutor, which must check Android permissions and platform restrictions.

## Structure

- `bozer_brain/models.py`: typed intent and action models.
- `bozer_brain/intent.py`: deterministic French/English intent extraction.
- `bozer_brain/planner.py`: converts intents into safe action proposals.
- `bozer_brain/safety.py`: confirmation and permission gates.
- `tests/`: automated tests for supported and unsupported requests.

## Safety boundary

This core never sends messages, places calls, launches apps, or creates alarms. It only returns a proposal. A real Android integration must request runtime permissions, show confirmation for sensitive actions, validate targets, and report execution results truthfully.

Unknown or ambiguous requests stay as chat; the core must not invent an action.

## Run tests

```bash
python -m unittest discover -s tests -v
```

## Current status

Foundation stage: deterministic intent parsing and safe planning. Android UI, floating overlay, model-backed conversation, persistent memory, and real device execution remain separate integration work.
