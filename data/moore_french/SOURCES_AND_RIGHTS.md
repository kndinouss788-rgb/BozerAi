# Mooré–French resources and integration status

Research checked on 2026-11-17. This register records the source, the license declared by its publisher and the language-review status of imported entries.

## Integrated source packs

| Resource | What it provides | BOZER use |
|---|---|---|
| MALM Data — `progressio-media/malm-data` | 10,258 Mooré–French–English lexical records. Its repository publishes an MIT license, Copyright (c) 2026 progressio-media. | 10,234 unique French–Mooré pairs are included in BOZER's working dictionary pack. The MIT notice is stored in `MALM_LICENSE.txt`; original source IDs are retained on every row. |
| Ouily et al. (2024), *A Low-Resource Language Translation: French To Mooré* | CC BY 4.0 research article with a small number of explicit lexical and sentence examples. | The cited examples are included with attribution and are still marked candidate pending native-speaker review. |

## Operational decision

The MALM publisher's repository explicitly declares MIT and provides the permission to use, modify, publish, distribute and sell copies, subject to retaining its copyright and license notice. BOZER therefore integrates the MALM dataset as a separately attributed third-party dictionary pack rather than copying or redistributing the 2020 dictionary PDF itself.

The MALM README names the underlying references, principally SRC-17 (the 2020 Mooré–French–English dictionary), as well as linguistic studies and learning materials. BOZER preserves those source IDs instead of hiding that provenance. The MIT grant is the license published by the MALM repository; it should not be represented as a separate written authorization from every author of the works cited by MALM.

To keep the product useful now without pretending that the language data has been speaker-certified:
- The normal dictionary and translator can return imported records as `candidate` / provisional results.
- Each returned candidate retains the source, source IDs and MIT notice reference.
- Ambiguous words return alternatives rather than choosing one meaning.
- The strict mode remains available with `include_candidates=False` or `allow_candidates=False`.
- No script or automated score promotes a candidate to `validated`.
- A candidate becomes validated only after a real Mooré speaker checks spelling, sense, tone, usage and region.

Upstream project and contact:
- Repository: https://github.com/progressio-media/malm-data
- License: https://github.com/progressio-media/malm-data/blob/main/LICENSE
- Dataset maintainer contact published in its README: Tuwend Nooma Jean Damase Roamba — tuwend.nooma@gmail.com

## Other resources reviewed

| Resource | What is known | Decision |
|---|---|---|
| Moore Lexique comprimé (Urs Niggli, Jan 2017) | Mooré–French–English, 8,080 entries. | Reference only; not copied into BOZER because no separate license is published with that PDF. |
| Mooré Burkina / Webonary | Online dictionary and audio; the site advertises thousands of audio files. | Useful pronunciation/verification reference; free access is not treated as a redistribution license. |
| MAFAND-MT / LaFand-MT, Mossi (mos-fr) | 2,287 training, 1,478 validation and 1,574 test examples in the dataset card. | Not used for commercial BOZER training because the corpus is marked CC-BY-4.0-NC. |
| Kndachraf, `moore-fr-translation` | About 6.74k rows mixing MAFAND-MT and other lexicon entries. | Not copied as a mixed dataset because a single clear license for all rows is not supplied. |
| `mooredataset/baoore-dataset-1` | 233,945 sentence pairs; dataset card marks CC BY-NC 4.0. | Not used for commercial BOZER without a separate commercial grant. |
| Translatewiki English–Mooré | Software localization sentences; dataset card lists CC BY 3.0. | Separate EN↔Mooré extension only; not silently converted into French–Mooré pairs. |

## References

- MALM Data: https://github.com/progressio-media/malm-data
- MAFAND-MT / LaFand-MT: https://github.com/masakhane-io/lafand-mt
- MAFAND-MT on Hugging Face: https://huggingface.co/datasets/masakhane/mafand
- Kndachraf aggregate: https://huggingface.co/datasets/Kndachraf/moore-fr-translation
- Translatewiki English–Mooré: https://huggingface.co/datasets/burkimbia/translatewiki-eng-mos
- Montivilliers-Nasséré: https://www.montivilliersnassere.fr/telechargements/vocabulaire.html
- Mooré Burkina: https://www.mooreburkina.com/
- 2020 dictionary PDF: https://mooreburkina.com/sites/www.mooreburkina.com/files/pdf-files/Dictionnaire%20Moor%C3%A9%20-%20%20fran%C3%A7ais%20-%20English.pdf
- Ouily et al. (2024), CC BY 4.0: https://hal.science/hal-04425412v1
