# Mooré–French resources and integration status

Research checked on 2026-11-15. This file records discovery, not permission.

| Resource | What it advertises | Current decision |
|---|---|---|
| Ouily et al. (2024), *A Low-Resource Language Translation: French To Mooré* | Article HAL-04425412, CC BY 4.0; contient quelques exemples de traduction | Utilisable avec attribution selon CC BY 4.0; exemples conservés comme candidats et soumis à validation native. La licence de l’article ne couvre pas automatiquement les textes sources cités dans sa méthodologie. |\n| MALM Data, progressio-media/malm-data | 10,258 lexicon entries, 33 grammar rules, 1,841 medical terms, 676 proverbs; repository declares MIT | Candidate source only. The README attributes content to third-party books, dictionaries and academic works. Obtain written confirmation that the uploader can license the underlying content for commercial redistribution before copying it into BOZER. |
| Moore Lexique comprimé (Urs Niggli, Jan 2017) | Mooré–French–English, 8,080 entries | Research reference; do not copy until redistribution rights are established. |
| Mooré Burkina / Webonary | Online dictionary and audio; site advertises thousands of audio files | Reference and potential permission/contact route; website availability is not a data license. |
| burkimbia/moore-parallel-corpus | French–Mooré parallel corpus | Gated and marked per-source-research-noncommercial; not suitable for commercial BOZER without a separate commercial license. |
| mooredataset/baoore-dataset-1 | Dataset card advertises 233,945 sentence pairs | Review exact terms, provenance and source-level rights before use; do not ingest automatically. |
| FLORES+ French–Mooré benchmark derivative | 997 test + 1,012 devtest sentences; CC BY-SA 4.0 per dataset card | Potential evaluation-only resource after verifying upstream license and attribution/share-alike obligations. Never mix evaluation splits into training. |

## Ingestion rules

1. Publicly downloadable does not mean public domain.
2. A repository-level MIT badge does not automatically clear third-party books reproduced in its data files.
3. Non-commercial-only datasets cannot train or ship a commercial BOZER product without separate permission.
4. Preserve source IDs, original orthography, tones/diacritics, dialect/region metadata and attribution.
5. Imported data remains candidate until reviewed by a native Mooré speaker.
6. Keep evaluation splits isolated from training.
7. Never fabricate phrases or compose sentences by concatenating dictionary glosses.

## Immediate path

Request commercial redistribution/training permission from the MALM maintainer and rights clarification for its primary dictionary source. The importer is prepared so a permitted export can be added without rewriting the translator.