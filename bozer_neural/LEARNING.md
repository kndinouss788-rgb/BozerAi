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
  --data data/bozer_neural_v0_4/train_fr.jsonl \
  --validation-data data/bozer_neural_v0_4/validation_fr.jsonl \
  --resume artifacts/bozer-neural-v0.4.pt \
  --output artifacts/bozer-neural-v0.4-next.pt \
  --steps 300
``

Le tokenizer du checkpoint est réutilisé pour garder les identifiants de tokens compatibles avec les poids déjà appris. Le modèle est affiné à partir de ces poids, au lieu de repartir de zéro.

## Apprentissage conversationnel

Les nouveaux exemples peuvent inclure un champ `history`, une liste ordonnée d'échanges précédents :

```json
{
  "category": "dialogue_naturel",
  "history": [
    {"user": "Je préfère les réponses courtes.", "assistant": "Compris."}
  ],
  "user": "Explique ce calcul.",
  "assistant": "Je vais donner les étapes simplement."
}
```

L'historique sert de contexte. La fonction de perte ne cible que la réponse courante de BOZER et son marqueur de fin. L'évaluateur réutilise le même format de conversation pour éviter un décalage entre entraînement et génération.

Pour lancer l'entraînement complet sur GitHub, utiliser le workflow manuel **BOZER NEURAL V0.4 full training** dans l'onglet Actions de la branche `bozer-neural-v0.1`. Il produit un checkpoint et un rapport de test si l'exécution arrive au bout.

## Limites et précautions

- L'approbation du corpus et l'entraînement sont deux opérations séparées.
- Une réponse proposée doit être vérifiée avant approbation ; une erreur approuvée peut enseigner une mauvaise réponse.
- Les données de validation et de test restent séparées et ne doivent jamais servir à l'entraînement.
- Une baisse de perte ne prouve pas à elle seule que BOZER sait mieux converser. Il faut aussi vérifier des exemples inédits, la qualité des réponses et les régressions.
- Ce mécanisme n'est pas encore intégré à l'application Android.


## Cerveau cible BOZER NEURAL 1B

Le modèle cible est séparé du prototype v0.4 : `BozerNeural1B` utilise un Transformer causal avec GQA, RoPE, RMSNorm, SwiGLU et embeddings liés. Sa configuration par défaut compte exactement 1 115 785 216 paramètres. Le code initialise des poids aléatoires ; il ne contient aucun poids entraîné.

Le corpus v0.5 rassemble 263 exemples d'entraînement, 60 de validation et 60 de test. Il contient six catégories équilibrées dans les partitions de validation et de test. Ces exemples sont synthétiques et restent beaucoup trop peu nombreux pour apprendre correctement un modèle 1B.

### Vérifier le corpus et les tests

```bash
python -m bozer_neural.validate_corpus \
  --train data/bozer_neural_v0_5/train_fr.jsonl \
  --validation data/bozer_neural_v0_5/validation_fr.jsonl \
  --test data/bozer_neural_v0_5/test_fr.jsonl

python -m pytest -q tests/test_bozer_neural_1b.py tests/test_bozer_neural_1b_training.py
```

### Entraîner le modèle cible

```bash
python -m bozer_neural.train_1b \
  --data data/bozer_neural_v0_5/train_fr.jsonl \
  --validation-data data/bozer_neural_v0_5/validation_fr.jsonl \
  --output artifacts/bozer-neural-1b.pt \
  --device cuda --precision bf16 \
  --steps 1000 --batch-size 1 --grad-accum-steps 16
```

Pour poursuivre un entraînement, ajouter `--resume artifacts/bozer-neural-1b.pt`. Le tokenizer et l'architecture du checkpoint doivent correspondre exactement. Le vocabulaire du tokenizer est réservé à 16 384 identifiants, même si le nombre de fusions apprises est encore inférieur ; les identifiants non appris sont masqués à la génération.

### Évaluer sans contaminer le test

```bash
python -m bozer_neural.evaluate_1b \
  --checkpoint artifacts/bozer-neural-1b.best.pt \
  --train data/bozer_neural_v0_5/train_fr.jsonl \
  --validation data/bozer_neural_v0_5/validation_fr.jsonl \
  --test data/bozer_neural_v0_5/test_fr.jsonl \
  --device cuda --report artifacts/bozer-neural-1b-test.json
```

L'évaluateur vérifie la séparation des trois partitions, calcule une perte de test par catégorie et affiche des réponses générées sur des exemples gardés à part.

### Avertissement matériel

Pour 1,1 milliard de paramètres, les poids FP32, les gradients et les deux états AdamW demandent à eux seuls environ 17,85 Go de mémoire. Les activations, buffers et le runtime s'ajoutent. Le script refuse donc par défaut une allocation CPU de grande taille et bloque un GPU dont la mémoire libre est inférieure à ce minimum théorique. Il faut davantage de mémoire en pratique. Aucun entraînement 1B n'est lancé par les tests CI.

Le dépôt contient maintenant l'architecture et le pipeline ; aucun checkpoint 1B entraîné n'a encore été produit et l'intégration Android reste à faire.
