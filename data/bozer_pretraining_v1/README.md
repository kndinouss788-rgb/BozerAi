# BOZER 1B pretraining corpus preparation

The 1B model needs a large, carefully curated text corpus before dialogue tuning.

**Current state (2026-11-15): this directory contains no raw training text and no train/validation/test corpus.** The repository includes only the preparation tool, this README and the source-rights audit. No external corpus has been ingested.

The source audit is in [SOURCES_AND_RIGHTS.md](SOURCES_AND_RIGHTS.md). Candidate sources are not approved automatically: verify the exact license, provenance, third-party rights, attribution and privacy conditions before importing any file.

## Preparation tool

Run the local preparation command:

    python -m bozer_neural.prepare_pretraining_corpus --input-dir data/raw_authorized_text --output-dir data/bozer_pretraining_v1 --source-manifest data/raw_authorized_text/source_manifest.json

The utility:
- extracts paragraphs and normalizes Unicode/whitespace;
- removes duplicate paragraphs using normalized SHA-256 hashes;
- redacts obvious email addresses, URLs and phone-like sequences;
- rejects blocks outside configured length bounds;
- assigns each source document wholly to one deterministic split (90% train, 5% validation, 5% test), so passages from the same source document cannot cross partitions;
- writes train.txt, validation.txt, test.txt and report.json.

It does not download content, inspect a license, verify commercial training rights or determine whether a source's license applies to model weights. The allowed_use field in the manifest is an owner declaration, not independent legal verification.

## Required source manifest

Every input .txt file must have an entry containing a unique source_id, title, author/publisher when known, source URL, exact license name and URL, retrieval date, attribution text and an explicit allowed_use decision. Keep the original file and its SHA-256 for provenance.

## Data stages

1. **Pretraining text:** broad, clean, legally authorized French text, source-isolated and deduplicated. This is the missing dataset.
2. **Supervised BOZER dialogue:** keep the existing categorized JSONL corpus separate; it teaches response format and behavior, not general French language knowledge.
3. **Validation/test:** never train on these partitions; preserve source- and prompt-level separation.

A small 100k–1M-token pilot can test ingestion, tokenization and quality controls. It is not enough to train the 1B target. As a rough compute-optimal planning heuristic, the Chinchilla research suggests scaling training tokens with parameter count; a 1.116B model would imply about 22.3B pretraining tokens at 20 tokens/parameter. This is a heuristic, not a universal minimum. See the [DeepMind paper](https://arxiv.org/abs/2203.15556).

Do not mistake a successful preparation report for proof that the corpus is legally cleared or that the model has learned useful capabilities.
