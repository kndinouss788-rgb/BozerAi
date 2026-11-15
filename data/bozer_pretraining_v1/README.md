# BOZER 1B pretraining corpus preparation

The 1B model needs a large, carefully curated text corpus before dialogue tuning.
This utility prepares deterministic train/validation/test partitions from local UTF-8
plain-text files. It does not download content, verify licenses, or make copyrighted
material safe to use. Only ingest public-domain, openly licensed, or otherwise
authorized sources, and retain provenance for every source.

## Usage

```bash
python -m bozer_neural.prepare_pretraining_corpus \
  --input-dir data/raw_authorized_text \
  --output-dir data/bozer_pretraining_v1
```

The utility:
- extracts paragraphs and normalizes Unicode/whitespace;
- removes duplicate paragraphs using normalized SHA-256 hashes;
- redacts obvious email addresses, URLs, and phone-like sequences;
- rejects blocks outside the configured length bounds;
- assigns each unique block deterministically to 90% train, 5% validation,
  and 5% test using its content hash, preventing identical blocks crossing splits;
- writes `train.txt`, `validation.txt`, `test.txt`, and `report.json`.

Review source licenses, provenance, factual quality, language balance, personal-data
handling, and split sizes before training. This is a preparation tool, not a corpus
and not evidence that BOZER has learned anything.
