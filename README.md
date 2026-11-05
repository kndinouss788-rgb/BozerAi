# BOZER AI

BOZER is a privacy-conscious assistant project designed to turn natural-language intentions into safe, understandable actions.

## Repository foundation

This repository currently contains a portable Python decision core. It is **not yet the Android application** and does not execute phone actions by itself.

### Core modules

- `bozer_brain/models.py`: typed intent and action models.
- `bozer_brain/intent.py`: deterministic French/English intent extraction.
- `bozer_brain/planner.py`: safe action proposals.
- `bozer_brain/safety.py` and `tools.py`: permission, confirmation, and tool contracts.
- `bozer_brain/task_planner.py`: dependency validation and task ordering.
- `bozer_brain/memory.py` and `context.py`: local SQLite history and bounded prompt context.
- `bozer_brain/model_gateway.py`: provider gateway and real OpenAI-compatible HTTPS adapter.
- `bozer_brain/eye_search.py`: bounded multi-source research orchestration.
- `bozer_brain/public_sources.py`: Wikipedia, Wikidata, and Brave Web Search adapters.
- `bozer_brain/dialogue.py`: optional research/model dialogue orchestration.
- `tests/`: automated tests.

## EYE_SEARCH: encyclopedic and wider-web sources

Available adapters:
- Wikipedia MediaWiki search API (public, no key).
- Wikidata entity search API (public, no key).
- Brave Web Search API (requires a Brave Search subscription token).

Brave is a genuine web-search API, not HTML scraping of a search page. Its token is sent only in the HTTPS request header. Configure it on the trusted host as `BOZER_BRAVE_SEARCH_API_KEY`; never embed it in an Android APK or commit it. Use French Wikimedia sources alongside Brave for broader coverage:

```python
import os
from bozer_brain.eye_search import EyeSearch
from bozer_brain.public_sources import (
    BraveSearchSource, WikipediaSearchSource, WikidataSearchSource,
)

sources = [
    BraveSearchSource(os.environ["BOZER_BRAVE_SEARCH_API_KEY"]),
    WikipediaSearchSource(language="fr"),
    WikidataSearchSource(language="fr"),
]
research = EyeSearch(sources).search("actualité scientifique", enabled=True)
```

The coordinator limits source count, result count, and snippet length. External text is marked as untrusted evidence, never instructions. Brave's API access and pricing depend on the user's own Brave Search plan.

## Connect a real language model

`OpenAICompatibleProvider` sends an HTTPS `POST /chat/completions` request with Bearer authentication. It can target OpenAI or another provider implementing the same API contract. The model identifier and API key are supplied by the host environment:

- `BOZER_MODEL_API_KEY` — secret API key.
- `BOZER_MODEL_NAME` — exact model identifier enabled for that key.
- `BOZER_MODEL_BASE_URL` — optional; defaults to `https://api.openai.com/v1`.
- `BOZER_MODEL_TIMEOUT` — optional; defaults to 25 seconds.

```python
from bozer_brain.model_gateway import ModelGateway, openai_provider_from_env

gateway = ModelGateway(openai_provider_from_env())
# DialogueEngine calls the gateway only when allow_model=True.
```

**Security rule:** do not put provider keys in Android source code, app resources, logs, or the APK. A production Android app should call a small authenticated BOZER backend; that backend keeps the provider key, applies user quotas and consent, and then calls the model. A real API request can incur charges. The adapter is implemented, but it is not activated until the owner supplies a provider key and model name.

## Dialogue integration

```python
from bozer_brain.dialogue import DialogueEngine
from bozer_brain.eye_search import EyeSearch

engine = DialogueEngine(memory, model_gateway=gateway, eye_search=EyeSearch(sources))
result = engine.respond(
    "Explique ce sujet", allow_search=True, allow_model=True
)
```

Phone-action intents still bypass research/model generation and remain proposals; Android must independently validate permissions, targets, and confirmations before execution.

## Privacy and execution boundaries

Conversation history remains in the configured local SQLite database unless the host explicitly sends prompt context to a remote model. Remote model calls and web searches require explicit opt-in. Search results are not treated as instructions. No provider credential is bundled in the repository.

The Python decision core is not yet the Android application: Android UI, floating overlay, secure backend deployment, and real device execution remain separate work.

## Run tests

```bash
python -m unittest discover -s tests -v
```
