# BOZER Mooré ↔ Français Translator — v0.2

## Purpose

Provide bidirectional Mooré–French word and exact-phrase lookup for BOZER IA, BOZER NEURAL and TTS. The working data pack contains 10,234 unique French–Mooré candidate pairs from MALM Data, plus the separately maintained BOZER seed examples. The upstream MALM repository declares an MIT license; its notice is preserved in `data/moore_french/MALM_LICENSE.txt`.

## Two translation directions

- French → Mooré: `translator.translate(text, "fr", "mos")`
- Mooré → French: `translator.translate(text, "mos", "fr")`

Both directions use the same normalized index and preserve Mooré diacritics.

## Result quality

- The normal working mode includes candidate records and returns a unique hit with `status="candidate"` and `confidence="provisional_exact"`.
- A candidate result includes its source, source IDs, source license and a note that native-speaker review has not been recorded.
- A reviewed entry is returned as `status="validated"` with `confidence="reviewed_exact"`.
- Ambiguous forms return `status="ambiguous"` and alternatives, never an arbitrary choice.
- Unknown phrases return `not_found`. The engine does not invent phrases by joining single-word translations.
- For reviewed-only output, call `load_default_translator(allow_candidates=False)`.

## Dictionary lookup

```python
from bozer_neural.language.dictionary import load_default_dictionary

dictionary = load_default_dictionary()
hits_fr = dictionary.exact("chien", "fr")
hits_mo = dictionary.exact("baaga¹", "mos")
matches = dictionary.search("famille", "fr", limit=10)
```

Use `load_default_dictionary(include_candidates=False)` for reviewed-only entries. Each hit exposes its status and source metadata.

## Translator lookup

```python
from bozer_neural.language import load_default_translator

translator = load_default_translator()
fr_to_mo = translator.translate("chien", "fr", "mos")
mo_to_fr = translator.translate("baaga¹", "mos", "fr")
print(fr_to_mo.translated_text, fr_to_mo.status, fr_to_mo.source_reference)
```

## Scope

This release is a real bilingual dictionary and exact word/phrase translator, not yet a fluent open-ended neural MT system. Full-sentence translation requires a reviewed parallel corpus, grammar-aware decoding, and evaluation by native Mooré speakers. The existing pipeline deliberately does not turn arbitrary word-by-word matches into a sentence.
