# BOZER NEURAL — corpus de 100 000 dialogues

Le corpus est généré depuis deux ensembles publics dont les fiches amont déclarent des licences permissives.

| Partition | Exemples | Usage |
|---|---:|---|
| train | 90 000 | Entraînement uniquement |
| validation | 5 000 | Suivi et sélection des paramètres |
| test | 5 000 | Évaluation finale, jamais d'entraînement |

Les dialogues sont stockés dans plusieurs fichiers JSONL compressés par partition pour maintenir une taille de fichier raisonnable. Les lecteurs BOZER acceptent directement le dossier de chaque partition.

Validation :
    python -m bozer_neural.validate_corpus --train data/bozer_neural_100k/train --validation data/bozer_neural_100k/validation --test data/bozer_neural_100k/test

Le fichier manifest.json conserve les révisions exactes, les licences déclarées, les volumes, les catégories, les langues et les SHA-256.

Il s'agit de 100 000 paires de dialogue importées, filtrées et normalisées, pas de 100 000 faits vérifiés indépendamment et pas d'un modèle déjà entraîné. Chaque exemple conserve sa provenance et le statut upstream_filtered_needs_sample_review. Un échantillonnage humain et une revue ciblée des droits restent nécessaires avant une diffusion commerciale des poids.

Le jeu de test BOZER reste séparé. Validation et test ne doivent jamais être déplacés dans l'entraînement.
