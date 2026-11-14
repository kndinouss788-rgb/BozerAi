# BOZER NEURAL V0.2 — corpus français et évaluation

## Modèle
Le noyau reste un Transformer causal décodeur initialisé aléatoirement : 2 blocs, 4 têtes, dimension 64, contexte 128 octets, tokenizer UTF-8 de 259 identifiants et 124 864 paramètres entraînables. Aucun poids pré-entraîné ni API distante.

## Corpus français V0.2
Dossier : `data/bozer_neural_v0_2/`.
- Entraînement : 72 exemples, 12 par catégorie.
- Validation : 12 exemples, 2 par catégorie.
- Test final : 12 exemples, 2 par catégorie.

Les six catégories couvrent le dialogue naturel, la compréhension, le raisonnement, la planification, les outils et la vérification, ainsi que la sécurité et l'honnêteté. Les données sont des exemples originaux synthétiques, pas des échanges réels ni une base de faits suffisante.

## Vérifier les fichiers et l'absence de questions répétées
`python -m bozer_neural.validate_corpus`

## Entraîner avec validation séparée
`python -m bozer_neural.train --steps 300`

La perte de validation est mesurée sans mise à jour des poids. Le fichier test n'est jamais utilisé pendant l'entraînement.

## Évaluer après l'entraînement
`python -m bozer_neural.evaluate --checkpoint artifacts/bozer-neural-v0.2.pt`

L'évaluation affiche la perte sur le fichier test et quelques productions du modèle à comparer aux réponses de référence. Cette mesure automatique ne suffit pas à prouver le raisonnement : une revue humaine et des tests ciblés restent nécessaires.

## Limites
La taille du corpus et du modèle est très faible. Le but de cette étape est de valider la chaîne de données, d'entraînement et de mesure, puis d'agrandir prudemment le corpus autorisé et dédupliqué. Le modèle n'est pas encore intégré à Android.
