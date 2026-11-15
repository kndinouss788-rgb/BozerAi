# BOZER NEURAL — apprentissage contrôlé

## Principe

Le cerveau n'apprend pas automatiquement tout ce qu'il entend. Un exemple proposé est d'abord placé dans une file d'attente, puis un humain le vérifie et l'approuve explicitement. L'approbation ajoute l'exemple au corpus d'entraînement, mais ne modifie pas immédiatement les poids du modèle.

## 1. Proposer un exemple

```bash
python -m bozer_neural.learning submit \
  --category dialogue_naturel \
  --user "Bonjour BOZER" \
  --assistant "Bonjour ! Comment puis-je t'aider ?"
```

Catégories disponibles : `dialogue_naturel`, `comprehension_demande`, `raisonnement_resolution`, `planification_missions`, `utilisation_outils_verification`, `securite_confidentialite_honnetete`.

Les propositions sont conservées dans `data/bozer_learning/pending_feedback.jsonl`.

## 2. Approuver après vérification

```bash
python -m bozer_neural.learning approve \
  --training data/bozer_neural_v0_5/train_fr.jsonl
```

On peut approuver un seul élément avec `--id ID`. Le module refuse les chemins de test et de validation et ignore les doublons. Il ne touche jamais aux fichiers de validation/test.

## 3. Premier entraînement de référence — corpus V0.5

Le premier entraînement exécutable vise volontairement le petit Transformer de référence, pas le modèle cible 1B :

- 2 blocs, 4 têtes, dimension 64 et contexte de 128 tokens ;
- tokenizer byte-level BPE appris uniquement sur la partition d'entraînement, jusqu'à 512 identifiants ;
- embeddings de tokens et de positions, attention causale, MLP GELU et poids d'embedding/sortie liés ;
- initialisation aléatoire, AdamW, warmup puis décroissance cosinus ;
- perte calculée uniquement sur la réponse de BOZER et son token EOS ;
- évaluation séparée par catégorie sur validation, puis une seule évaluation finale sur le test tenu à l'écart.

Le corpus V0.5 contient 263 dialogues d'entraînement, 60 de validation et 60 de test, répartis dans six catégories. Les exemples sont synthétiques et servent à éprouver le pipeline ; ils ne suffisent pas à enseigner le français général à un assistant.

Le workflow **BOZER NEURAL initial baseline training (corpus v0.5)** entraîne depuis des poids aléatoires sur la branche isolée `bozer-neural-v0.1`, puis évalue les 60 exemples de test. Il publie le checkpoint et le rapport sous forme d'artifact GitHub Actions. Il ne publie rien dans l'application Android.

Commande locale équivalente :

```bash
python -m bozer_neural.validate_corpus \
  --train data/bozer_neural_v0_5/train_fr.jsonl \
  --validation data/bozer_neural_v0_5/validation_fr.jsonl \
  --test data/bozer_neural_v0_5/test_fr.jsonl

python -m bozer_neural.train \
  --data data/bozer_neural_v0_5/train_fr.jsonl \
  --validation-data data/bozer_neural_v0_5/validation_fr.jsonl \
  --steps 600 --batch-size 8 --grad-accum-steps 2 \
  --eval-interval 25 --eval-batches 4 --warmup-steps 20 \
  --early-stopping-patience 5 \
  --output artifacts/bozer-neural-v0.4-corpus-v0.5.pt

python -m bozer_neural.evaluate \
  --checkpoint artifacts/bozer-neural-v0.4-corpus-v0.5.best.pt \
  --data data/bozer_neural_v0_5/test_fr.jsonl \
  --samples 60 --report artifacts/bozer-neural-corpus-v0.5-test-report.json
```

Le format interne du checkpoint reste `bozer-neural-v0.4` : V0.5 désigne ici la version du corpus, pas une rupture de format des poids.

## 4. Reprendre un entraînement

Après approbation d'exemples supplémentaires, reprendre un checkpoint en gardant le tokenizer appris et les identifiants compatibles :

```bash
python -m bozer_neural.train \
  --data data/bozer_neural_v0_5/train_fr.jsonl \
  --validation-data data/bozer_neural_v0_5/validation_fr.jsonl \
  --resume artifacts/bozer-neural-v0.4-corpus-v0.5.pt \
  --output artifacts/bozer-neural-v0.4-corpus-v0.5-next.pt \
  --steps 300
```

Le modèle est alors affiné à partir de son checkpoint ; ce n'est plus un nouvel entraînement depuis zéro.

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

## 5. Modèle cible BOZER NEURAL 1B

Le modèle cible est séparé du prototype de référence : `BozerNeural1B` utilise un Transformer causal avec GQA, RoPE, RMSNorm, SwiGLU et embeddings de tokens liés à la sortie. Sa configuration par défaut compte exactement 1 115 785 216 paramètres. Le code initialise des poids aléatoires ; il ne contient aucun poids entraîné.

Le corpus V0.5 est beaucoup trop petit pour apprendre correctement un modèle 1B. Il permet seulement de vérifier le chargeur, la boucle et le format sur des configurations minuscules.

### Vérifier le corpus et les tests

```bash
python -m bozer_neural.validate_corpus \
  --train data/bozer_neural_v0_5/train_fr.jsonl \
  --validation data/bozer_neural_v0_5/validation_fr.jsonl \
  --test data/bozer_neural_v0_5/test_fr.jsonl

python -m pytest -q tests/test_bozer_neural_1b.py \
  tests/test_bozer_neural_1b_training.py \
  tests/test_bozer_neural_1b_evaluation.py
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

Pour poursuivre un entraînement, ajouter `--resume artifacts/bozer-neural-1b.pt`. Le tokenizer et l'architecture du checkpoint doivent correspondre exactement. Le vocabulaire du tokenizer est réservé à 16 384 identifiants, même si le nombre de fusions apprises est encore inférieur ; les identifiants non appris sont masqués à la génération. Le checkpointing des gradients est activé par défaut pour réduire les activations conservées en mémoire.

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

## 6. Besoins matériels et limites

Pour le petit modèle de référence, le nombre de paramètres est de 124 864 avec le vocabulaire byte de base (259 tokens) et au maximum 141 056 avec un vocabulaire BPE de 512 tokens. Les poids FP32, gradients et deux états AdamW représentent environ 2,3 Mo à ce maximum, hors activations, buffers et runtime PyTorch. C'est une taille adaptée à un premier test CPU.

Pour 1,1 milliard de paramètres, les poids FP32, les gradients et les deux états AdamW demandent à eux seuls 17 852 563 456 octets (environ 17,85 Go décimaux). Les activations, buffers et le runtime s'ajoutent. Le script refuse donc par défaut une allocation CPU de grande taille et bloque un GPU dont la mémoire libre est inférieure à ce minimum théorique. Il faut davantage de mémoire en pratique.

Aucune perte en baisse ne prouve à elle seule que BOZER sait mieux converser. Il faut comparer les réponses générées aux références, noter les erreurs par catégorie et tester des demandes réellement inédites. Aucun entraînement 1B complet n'a été lancé ; le checkpoint 1B n'existe pas encore et l'intégration Android reste à faire.
