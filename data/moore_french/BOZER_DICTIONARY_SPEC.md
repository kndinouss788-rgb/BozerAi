# BOZER Mooré–Français — dictionnaire original et intégré

## Mission

Provide a usable bidirectional Mooré–French dictionary and exact phrase translator, with source provenance and explicit quality status. The working dictionary includes the MALM data pack under the MIT notice published by its upstream repository and separately attributed in `MALM_LICENSE.txt`.

## Entry quality

- `candidate`: usable as a provisional lookup result; not yet checked by a native Mooré speaker.
- `validated`: checked by a native speaker, with source, reviewer pseudonym, date and license information.
- `rejected`: excluded from results.
- Candidate results must never be labeled as verified or fluent translations.
- Multiple senses or translations must be returned as alternatives, not arbitrarily collapsed.
- Keep Mooré diacritics, tones, region, register and grammatical information where the source provides them.
- Unknown full sentences must remain `not_found`; do not fabricate Mooré by concatenating dictionary glosses.

## Required fields

| Field | Required | Description |
|---|---:|---|
| id | yes | Stable unique entry identifier |
| moore | yes | Mooré form, preserving diacritics |
| french | yes | French sense |
| status | yes | `candidate`, `validated` or `rejected` |
| category | recommended | `word`, `phrase`, `expression`, `proverb` or `sentence` |
| source | yes | Source publisher or contributor |
| source_reference | yes | Original source ID / page / entry reference where known |
| source_license | yes | Declared source license |
| rights_status | recommended | Provenance/licensing status string |
| reviewed_by | validation | Native-speaker reviewer pseudonym |
| reviewed_at | validation | ISO date of review |
| region | no | Confirmed regional variety |
| part_of_speech | no | Noun, verb, adjective, particle, etc. |
| pronunciation | no | Confirmed pronunciation / phonetic transcription |
| tone_notes | no | Confirmed tone distinctions |
| example_moore | no | Reviewed original example |
| example_french | no | Reviewed translation of the example |
| note | no | Context or ambiguity notes |

## Translator behavior

- Language codes: `fr` and `mos`.
- Normal mode loads the complete working candidate dictionary and returns unique matches as `candidate` with `provisional_exact` confidence.
- Strict mode is available with `load_default_translator(allow_candidates=False)`.
- Reverse translation Mooré→French and French→Mooré use the same index.
- Exact dictionary/phrase matches only; no automatic word-by-word sentence construction.
- Ambiguous forms return `ambiguous` and a list of alternatives.
- Each result exposes source, source reference, source license, rights status and reviewer metadata.

## Use

```python
from bozer_neural.language import load_default_translator

translator = load_default_translator()
result = translator.translate("chien", "fr", "mos")
if result.found:
    print(result.translated_text, result.status, result.source_reference)
else:
    print(result.status, result.note)
```

For reviewed-only lookup:

```python
translator = load_default_translator(allow_candidates=False)
```

## Evaluation and future model

Keep test examples reviewed and separate from the dictionary/training data. A neural model is a separate milestone: it must be trained on rights-cleared parallel sentences and evaluated with a held-out, native-speaker-reviewed test set. The current exact translator must not be described as open-ended fluent machine translation.
