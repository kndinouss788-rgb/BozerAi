# Corpus BOZER NEURAL v0.5 (français)

Ce jeu de données reprend les partitions v0.4 et ajoute 84 exemples synthétiques originaux contrôlés : 60 entraînement, 12 validation et 12 test, répartis entre les six catégories BOZER.

- Entraînement : 264 exemples.
- Validation : 60 exemples.
- Test : 60 exemples.
- Les nouveaux scénarios portent un identifiant `v0_5_...` et l'origine `original_synthetic_curated`.
- Les partitions validation/test ne doivent jamais être utilisées pour l'entraînement.

Les exemples sont synthétiques, rédigés pour les tests de comportement et non des faits vérifiés ni un corpus de pré-entraînement à grande échelle. Une revue humaine et des corpus français légalement réutilisables restent nécessaires.

Validation :

```bash
python -m bozer_neural.validate_corpus \
  --train data/bozer_neural_v0_5/train_fr.jsonl \
  --validation data/bozer_neural_v0_5/validation_fr.jsonl \
  --test data/bozer_neural_v0_5/test_fr.jsonl
```
