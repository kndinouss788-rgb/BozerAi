# Mooré–French resources and integration status

Research checked on 2026-11-15. This file records provenance and release restrictions.

| Resource | What it advertises | Current decision |
|---|---|---|
| Ouily et al. (2024), *A Low-Resource Language Translation: French To Mooré* | Article HAL-04425412, CC BY 4.0; contains a few explicit translation examples | Used only for the small cited examples already present in the primary lexicon, with attribution. The article's license does not automatically cover texts cited in its methodology. |
| MALM Data, progressio-media/malm-data | Upstream repository declares MIT; lexicon lists 10,258 Mooré–French–English records | 10,234 new unique pairs imported to `malm_candidates.jsonl` after collapsing 23 repeated pairs and excluding 1 pair already in BOZER. Every record remains a candidate, not an approved translation. The upstream file attributes most records to SRC-17 (9,515 records) and also cites other books/manuals. The MIT declaration is recorded as the upstream repository's claim; it does not independently establish permissions from every underlying source rightsholder. Do not mark these entries validated or treat the bulk corpus as production-cleared until source-level rights are clarified. |
| Moore Lexique comprimé (Urs Niggli, Jan 2017) | Mooré–French–English, 8,080 entries | Research reference; do not copy until redistribution rights are established. |
| Mooré Burkina / Webonary | Online dictionary and audio; site advertises thousands of audio files | Reference and potential permission/contact route; website availability is not a data license. |
| burkimbia/moore-parallel-corpus | French–Mooré sentence and term pairs | Marked per-source-research-noncommercial with mixed source rights; not suitable for commercial BOZER without a separate commercial license. |
| mooredataset/baoore-dataset-1 | Dataset card advertises 233,945 sentence pairs | CC BY-NC 4.0; excluded from commercial BOZER use without separate permission. |
| FLORES+ French–Mooré benchmark derivative | 997 test + 1,012 devtest sentences; CC BY-SA 4.0 per dataset card | Potential evaluation-only resource after verifying upstream license and attribution/share-alike obligations. Never mix evaluation splits into training. |

## MALM import audit

- Upstream records: 10258.
- New unique French–Mooré pairs placed in BOZER: 10234.
- Exact duplicate pairs collapsed: 23.
- Pairs already present in the BOZER primary lexicon and therefore not re-added: 1.
- Records missing a Mooré or French field: 0.
- BOZER primary lexicon before import: 15; total candidate + primary records after loading both files: 10249.
- Repeated Mooré forms and repeated French glosses are deliberately retained when the paired meaning differs; they may represent polysemy or synonyms and must not be flattened into one guessed answer.
- The entire imported batch is marked candidate. It is excluded from ordinary translation and can be inspected only when candidate mode is explicitly enabled. No native-speaker review has been claimed.

## Ingestion and rights rules

1. Publicly downloadable does not mean public domain.
2. A repository-level license declaration does not automatically clear third-party books reproduced in its data files.
3. Non-commercial-only datasets cannot train or ship a commercial BOZER product without separate permission.
4. Preserve source IDs, original orthography, tones/diacritics, dialect/region metadata and attribution.
5. Imported data remains candidate until reviewed by a native Mooré speaker.
6. Keep evaluation splits isolated from training.
7. Never fabricate phrases or compose sentences by concatenating dictionary glosses.

## Operational boundary

BOZER's default translator continues to use validated entries only. The 10,234 MALM-derived candidates are loaded only when a caller explicitly requests `allow_candidates=True`. Multiple valid senses or competing forms return an `ambiguous` result rather than an arbitrary translation. The imported records are a research queue, not a claim of 10,234 verified or production-ready translations.
