# BOZER NEURAL V0.1 — base entraînable et évaluation

## Architecture
- Transformer causal décodeur, 2 blocs, 4 têtes.
- Dimension cachée 64 ; MLP 256 ; contexte 128 octets.
- Tokenizer UTF-8 octet par octet : 259 identifiants.
- Initialisation aléatoire, sans poids pré-entraînés.
- **124 864 paramètres entraînables**.

## Corpus et validation
Le texte est séparé en deux segments contigus : 90 % pour l'entraînement et 10 % pour la validation par défaut. Le segment de validation n'est jamais utilisé pour mettre à jour les poids. Il faut au moins contexte + 2 tokens dans chaque segment. La séparation contiguë réduit les fuites entre fenêtres voisines.

Le corpus fourni est uniquement un corpus français de démonstration, trop petit pour créer un assistant généraliste. Il faut construire un corpus autorisé, diversifié, nettoyé et dédupliqué avant tout entraînement sérieux.

## Commandes
Installer : `python -m pip install -r requirements-neural.txt`
Tests : `python -m unittest discover -s tests -p "test_bozer_neural.py" -v`
Entraînement expérimental : `python -m bozer_neural.train --steps 300`

À intervalles réguliers, le programme mesure la perte d'entraînement et de validation sans gradient. La perte mesure la prédiction statistique des prochains octets ; elle ne prouve pas à elle seule la compréhension ou le raisonnement.

Le checkpoint .pt et son JSON compagnon contiennent les poids, la configuration, les tailles des segments et l'historique des pertes. Les poids générés ne sont pas ajoutés au dépôt.

## Limites
Ce noyau n'est pas encore relié à Android et ne démontre pas de raisonnement général. Les performances sur téléphone restent à mesurer.
