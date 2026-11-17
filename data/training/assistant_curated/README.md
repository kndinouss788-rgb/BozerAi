# BOZER assistant-curated training seed

The file `bozer_world_knowledge_seed.jsonl` is a first authored teaching set created for BOZER.

## Record format

Each JSONL line contains:
- `instruction`: a user question or task;
- `response`: a model answer demonstrating a useful response pattern;
- `category`: broad subject area;
- `language`: language of the example;
- `source`: provenance label;
- `review_status`: review state.

## Important limitations

- This is a small seed set, not a complete knowledge base and not enough to train a capable general assistant.
- The examples are authored teaching material, not copied internal GPT model weights or hidden training data.
- Every record is marked `needs_fact_review`. Review facts, wording, safety, and suitability before promoting examples into a production training corpus.
- Do not treat generated examples as authoritative merely because they were written fluently.
- Expand the corpus in balanced subject areas and keep a held-out evaluation set separate from training data.
- Do not place private conversation details or personal information into the general world-knowledge corpus.
