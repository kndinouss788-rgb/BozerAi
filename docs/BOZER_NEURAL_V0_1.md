# BOZER NEURAL V0.3 — tokenizer appris et chaîne d'évaluation

## Modèle
Le noyau reste un Transformer causal décodeur initialisé aléatoirement : 2 blocs, 4 têtes, dimension 64 et contexte de 128 tokens. Les poids du modèle sont créés depuis zéro. Aucun modèle pré-entraîné ni API distante n'est utilisé comme cerveau.

## Tokenizer byte-level BPE
Le vocabulaire commence avec 259 identifiants (trois marqueurs et 256 octets UTF-8), puis apprend des fusions de paires directement depuis le texte du fichier d'entraînement. Le vocabulaire de validation et de test n'est jamais utilisé pour créer les fusions. Le tokenizer complet est enregistré avec le checkpoint afin que l'évaluation utilise exactement les mêmes identifiants.

Le but est de représenter les morceaux fréquents du français avec moins de tokens qu'un encodage octet par octet. Le tokenizer demeure réversible sur du texte UTF-8.

## Corpus français V0.2
Dossier : `data/bozer_neural_v0_3/`.
- Entraînement : 108 exemples, 18 par catégorie.
- Validation : 12 exemples, 2 par catégorie.
- Test final : 12 exemples, 2 par catégorie.

Les six catégories couvrent le dialogue naturel, la compréhension, le raisonnement, la planification, les outils et la vérification, ainsi que la sécurité et l'honnêteté. Les données sont des exemples originaux synthétiques, pas des échanges réels ni une base de faits suffisante.

## Vérifier les fichiers et l'absence de questions répétées
`python -m bozer_neural.validate_corpus`

## Entraîner depuis zéro
`python -m pip install -r requirements-neural.txt`
`python -m bozer_neural.train --steps 300`

La perte d'entraînement et celle de validation sont mesurées séparément. Le vocabulaire BPE est appris uniquement sur le fichier d'entraînement. Le test final n'est jamais utilisé pendant l'entraînement.

## Évaluer après l'entraînement
`python -m bozer_neural.evaluate --checkpoint artifacts/bozer-neural-v0.3.pt`

L'évaluation affiche la perte sur le fichier test et quelques productions du modèle à comparer aux réponses de référence. Le checkpoint contient les poids, la configuration et le tokenizer correspondant.

## Limites
Le corpus V0.3 n'a que 108 exemples d'entraînement et le modèle reste minuscule. Un entraînement de démonstration validera la chaîne technique, pas une compétence conversationnelle fiable. Une revue humaine et des tests ciblés sont indispensables. Le modèle n'est pas encore intégré à Android.
