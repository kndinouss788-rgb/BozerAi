# BOZER global language strategy

Status: architecture direction, 17 November 2026

## Product decision

BOZER is a global assistant first. French–Mooré is a personal language pack maintained for the founder's own use and as a possible future community benefit; it is not the product's identity, launch market, or a prerequisite for the global release.

The international language layer must be independent from the Mooré dictionary. A user should be able to select a source and target language without BOZER assuming that every language pair has the same model, dictionary, or quality level.

## Recommended architecture

1. **Global language interface:** one language registry and a provider-neutral translation contract. The application asks for a language pair; it does not hard-code a specific model into chat, voice, or UI.
2. **Global translation backend:** start by evaluating a self-hosted multilingual model. Keep translation as a service behind a replaceable adapter so the assistant can use cloud inference when connected and an eligible local model when one is available.
3. **Pair-specific language packs:** retain the French–Mooré dictionary and its provenance/review metadata as an optional pack. It must not be loaded as the default dictionary for every user.
4. **Voice pipeline:** speech recognition, translation, and speech synthesis remain separate capabilities. A model supporting text translation does not automatically provide ASR or TTS.
5. **Quality states:** distinguish model-generated, dictionary candidate, and human-reviewed output. Never label a model output as validated merely because inference succeeded.

## Model leads to evaluate

### Index-Translate 2B — first global prototype candidate

The publisher's repository describes a multilingual text/image translation family covering 150 languages, offers a 2B checkpoint, and publishes the repository under Apache-2.0. This is a promising first candidate for a self-hosted global text translation experiment. The public demo/API is useful for a temporary benchmark only; BOZER production should not depend on an uncommitted free endpoint. Confirm the exact checkpoint card, dependencies, language-pair coverage, model-weight terms, memory footprint, and serving cost before deployment.

### Google MADLAD-400 3B — broad-coverage comparison candidate

The official model documentation describes the MADLAD-400 family as covering 400+ languages, and the 3B checkpoint is a useful comparison for broad language coverage. Its size makes it a server-side evaluation candidate first, not a default download for ordinary Android phones. Confirm the exact checkpoint's license and benchmark each target pair before adoption.

### QVAC TranslatePsy-AfriSLM — African-language expansion research

QVAC publishes models for English and 19 Sub-Saharan African languages, with 0.8B, 2B and 4B variants. This is relevant to a later African language pack, but its published list does not include Mooré. There is also a licensing distinction to resolve: the model card says Apache-2.0 for weights, while the publisher's product page describes the release as for research and education and marks the released synthetic training data CC-BY-NC-4.0. Do not bundle or commercially deploy it until the publisher confirms the intended commercial rights in writing. It is not a global translation solution by itself.

## First implementation and evaluation sequence

1. Keep the existing French–Mooré dictionary pipeline isolated as a personal language pack.
2. Define a provider-neutral language-pair interface and a registry; do not hard-code the global pipeline to NLLB language tokens.
3. Prototype Index-Translate 2B and MADLAD-400 3B on the same fixed test set covering French, English, Spanish, Portuguese, Arabic, German, Chinese, and selected African languages.
4. Record per-direction adequacy, terminology, hallucinations, latency, peak RAM/VRAM, model size, and serving cost. Do not rely on a single averaged benchmark.
5. Choose a server model for connected global translation first. Add local/offline models only for pairs that meet device, license, and quality targets.
6. Keep Mooré tests, data rights, and native-speaker review separate from the global benchmark.

## Decision not to make yet

No candidate is selected for production in this document. The research identifies credible candidates, not proven BOZER performance. No model weights have been downloaded, run, or benchmarked by BOZER yet.

## Research sources

- Index-Translate repository and published language/feature description: https://github.com/bilibili/Index-Translate
- Index-Translate Apache-2.0 license: https://github.com/bilibili/Index-Translate/blob/main/LICENSE
- Google MADLAD-400 model card: https://huggingface.co/google/madlad400-10b-mt
- Hugging Face Transformers MADLAD-400 documentation: https://huggingface.co/docs/transformers/main/model_doc/madlad-400
- QVAC TranslatePsy official model page and license notes: https://qvac.tether.io/models/translatepsy
- TranslatePsy-AfriSLM model card: https://huggingface.co/qvac/TranslatePsy-AfriSLM-4B
