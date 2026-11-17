# Sources et conditions du corpus conversationnel BOZER 100k

## Sources

1. OpenAssistant/oasst1 — https://huggingface.co/datasets/OpenAssistant/oasst1  
La fiche amont déclare Apache-2.0. Le corpus contient des messages crowdsourcés et annotés. BOZER utilise la révision fdf72ae0827c1cda404aff25b6603abec9e3399b et le fichier 2023-04-12_oasst_ready.messages.jsonl.gz.

2. HuggingFaceH4/ultrachat_200k — https://huggingface.co/datasets/HuggingFaceH4/ultrachat_200k  
La fiche amont déclare MIT. Le sous-ensemble train_sft contient des dialogues en anglais. La révision exacte utilisée est inscrite dans manifest.json.

## Filtres appliqués

- Messages OASST supprimés ou rejetés exclus.
- Messages OASST signalés comme spam, PII, inappropriés, haineux, sexuels ou à toxicité élevée exclus.
- Textes vides, trop courts ou trop longs exclus.
- Motifs évidents d'adresse e-mail et de numéro de téléphone exclus.
- Prompts identiques après normalisation supprimés entre les sources.
- Un exemple conservé par arbre OASST et par conversation UltraChat.
- Groupes de conversation séparés entre train, validation et test ; fuites de prompts contrôlées.

## Attribution et limites

Les licences indiquées sont celles déclarées dans les fiches amont. Elles sont conservées dans chaque ligne et dans manifest.json. Cela ne constitue pas un avis juridique et ne confirme pas indépendamment les droits de chaque texte sous-jacent, notamment les réponses générées par modèle présentes dans UltraChat. Les exemples n'ont pas tous reçu une revue factuelle humaine. Un échantillonnage humain et une revue des droits restent nécessaires avant commercialisation ou distribution des poids.

Aucune conversation privée de l'utilisateur BOZER n'est intégrée au corpus.
