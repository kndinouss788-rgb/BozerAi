# BOZER NEURAL — apprentissage contrôlé

## Principe

Le cerveau n'apprend pas automatiquement tout ce qu'il entend. Un exemple proposé est d'abord placé dans une file d'attente, puis un humain le vérifie et l'approuve explicitement. L'approbation ajoute l'exemple au corpus d'entraînement, mais ne modifie pas immédiatement les poids du modèle.

## 1. Proposer un exemple

```bash
python -m bozer_neural.learning submit \
  --category dialogue_naturel \
  --user "Bonjour BOZER" \
  --assistant "Bonjour ! Comment puis-je t'aider ?"
``

Catégories disponibles : `dialogue_naturel`, `comprehension_demande`, `raisonnement_resolution`, `planification_missions`, `utilisation_outils_verification`, `securite_confidentialite_honnetete`.

Les propositions sont conservées dans `data/bozer_learning/pending_feedback.jsonl`.

## 2. Approuver après vérification

```bash
python -m bozer_neural.learning approve \
  --training data/bozer_neural_v0_4/train_fr.jsonl
``

On peut approuver un seul élément avec `--id ID`. Le module refuse les chemins de test et de validation et ignore les doublons. Il ne touche jamais aux fichiers de validation/test.

## 3. Entraîner une nouvelle version à partir d'un checkpoint

Après approbation, lancer un nouvel entraînement en reprenant le checkpoint précédent :

```bash
python -m bozer_neural.train \
  --data data/bozer_neural_v0_3/train_fr.jsonl \
  --validation-data data/bozer_neural_v0_3/validation_fr.jsonl \
  --resume artifacts/bozer-neural-v0.4.pt \
  --output artifacts/bozer-neural-v0.4.pt \
  --steps 600
``

Le tokenizer du checkpoint est réutilisé pour garder les identifiants de tokens compatibles avec les poids déjà appris. Le modèle est affiné à partir de ces poids, au lieu de repartir de zéro.

## Limites et précautions

- L'approbation du corpus et l'entraînement sont deux opérations séparées.
- Une réponse proposée doit être vérifiée avant approbation ; une erreur approuvée peut enseigner une mauvaise réponse.
- Les données de validation et de test restent séparées et ne doivent jamais servir à l'entraînement.
- Une baisse de perte ne prouve pas à elle seule que BOZER sait mieux converser. Il faut aussi vérifier des exemples inédits, la qualité des réponses et les régressions.
- Ce mécanisme n'est pas encore intégré à l'application Android.
