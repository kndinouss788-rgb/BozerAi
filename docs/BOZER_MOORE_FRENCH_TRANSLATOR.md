# BOZER Mooré ↔ Français Translator — v0.1

## Purpose

Provide a conservative, auditable translation component that can later be called
by BOZER IA, BOZER NEURAL and BOZER TTS. The first implementation is an exact
phrase/lexicon lookup engine. It is deliberately not described as a complete
machine-translation model.

## Safety and linguistic quality

- Language identifiers: `fr` (French) and `mos` (Mooré).
- Only entries with `status: validated` are returned by default.
- Candidate entries can be inspected explicitly for research, but are not
  treated as reliable translations.
- A validated entry must include a source and a named native-speaker reviewer.
- Unknown phrases return `not_found`; the component never fabricates a Mooré
  sentence by joining dictionary words.
- Unicode NFC normalization preserves Mooré diacritics.
- Each entry should eventually include dialect/region, part of speech, sense,
  usage context, example sentences, audio, source/license and reviewer details.

## Current seed data

The lexicon contains one documentary candidate for a morning greeting:
`Ne y yibeoogo` ↔ `Bonjour (le matin)`. It is marked `candidate`, not
validated, because no native-speaker review has yet been recorded. It is
therefore excluded from normal translation output.

## Use

```python
from bozer_neural.language import load_default_translator

translator = load_default_translator()
result = translator.translate("Bonjour (le matin)", "fr", "mos")
if result.found:
    print(result.translated_text)
else:
    print("Traduction Mooré non vérifiée ou indisponible.")
```

For research inspection only:

```python
translator = load_default_translator(allow_candidates=True)
```

## Next development stages

1. Add sourced entries from lawfully usable dictionaries and learning materials.
2. Record provenance and rights for every source.
3. Ask native Mooré speakers to validate spelling, meaning, register and regional
   usage; reject uncertain entries.
4. Add phrase-level examples and contextual alternatives.
5. Add evaluation sets reviewed by speakers, with held-out examples.
6. Only after enough reviewed parallel data exists, evaluate a statistical or
   neural translation model. Do not claim fluent Mooré before that evaluation.

This translator is a shared language service. It does not replace the original
BOZER NEURAL model and does not silently outsource all language understanding
to an external API.
