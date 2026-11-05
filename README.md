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
- `bozer_brain/eye_search.py`: bounded multi-source research orchestration and untrusted-source context.
- `bozer_brain/public_sources.py`: real public Wikipedia and Wikidata search adapters using Python's standard library.
- `bozer_brain/dialogue.py`: conversation orchestration with optional research context and injected responder.
- `tests/`: automated tests.

## EYE_SEARCH public sources

Two read-only public sources are implemented:
- Wikipedia via the MediaWiki Action API search module.
- Wikidata via its public `wbsearchentities` API.

Both use fixed HTTPS endpoints, a descriptive User-Agent, bounded results, a 5-second default network timeout, and a 1 MB response ceiling. No API key, paid service, crawler, or arbitrary URL fetch is used. Language defaults to English and can be set to another short language code (for example `fr`).

Example:

```python
from bozer_brain.eye_search import EyeSearch
from bozer_brain.public_sources import WikipediaSearchSource, WikidataSearchSource

research = EyeSearch([
    WikipediaSearchSource(language="fr"),
    WikidataSearchSource(language="fr"),
]).search("histoire du Burkina Faso", enabled=True)
```

EYE_SEARCH output is explicitly marked as untrusted external data. It is evidence to verify, not instructions to follow.

## Dialogue integration

Pass an `EyeSearch` instance to `DialogueEngine`. A normal chat turn only searches when the caller explicitly uses `allow_search=True`; the model still requires its separate `allow_model=True` opt-in. Phone-action intents never trigger research.

```python
engine = DialogueEngine(
    memory,
    model_gateway=model_gateway,
    eye_search=EyeSearch([
        WikipediaSearchSource(language="fr"),
        WikidataSearchSource(language="fr"),
    ]),
)
result = engine.respond(
    "Explique le sujet", allow_search=True, allow_model=True
)
```

The dialogue layer attaches the bounded research context to the model prompt and returns the research object in `DialogueResult.research`. It does not claim sources prove the answer.

## Privacy and model integration

The application chooses the SQLite database path. History stays in that local database unless a separate integration sends context elsewhere. The model gateway refuses provider calls unless explicitly enabled. No AI provider or API key is bundled by default. Provider credentials, network calls, retention rules, and user consent must be implemented separately.

## Planning and execution boundary

The task planner validates dependencies and orders steps. Each step must pass the tool registry's argument, permission, and confirmation gates. A ready step is only authorized for a future Android adapter; it is **not executed** by this Python core. Android must independently validate permissions, targets, confirmation, and execution results.

Unknown or ambiguous requests stay unplanned rather than being converted into guessed actions.

## Run tests

```bash
python -m unittest discover -s tests -v
```

## Current status

Decision core, local memory, bounded conversation context, tool authorization, dependency-aware task planning, provider-neutral model gateway, live public-source adapters, and dialogue research integration are implemented as foundations. Android UI, floating overlay, connected language-model provider, persistent Android integration, and real device execution remain separate work.
