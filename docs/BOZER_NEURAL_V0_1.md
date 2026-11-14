# BOZER NEURAL V0.4 — entraînement reproductible et évaluation

## État de l'architecture

Le moteur est un Transformer causal décodeur initialisé aléatoirement : 2 blocs, 4 têtes, dimension 64 et contexte de 128 tokens. Les poids sont créés depuis zéro. Le tokenizer byte-level BPE apprend ses fusions uniquement à partir de la partition d'entraînement. Aucun poids pré-entraîné n'est chargé dans BOZER NEURAL.

Le modèle de base possède 124 864 paramètres entraînables avec le vocabulaire initial de 259 tokens ; le vocabulaire BPE agrandi augmente ce nombre. C'est volontairement un très petit modèle expérimental, pas encore un assistant généraliste.

## Corpus V0.4

Dossier : `data/bozer_neural_v0_4/`.
- Entraînement : 180 exemples, 30 par catégorie.
- Validation : 36 exemples, 6 par catégorie.
- Test final : 36 exemples, 6 par catégorie.
- Total : 252 exemples synthétiques.

Les six catégories couvrent le dialogue naturel, la compréhension, le raisonnement, la planification, les outils et la vérification, puis la sécurité, la confidentialité et l'honnêteté. Les 72 ajouts d'entraînement sont originaux et identifiés par `scenario_id`. Validation et test sont séparés ; le validateur vérifie les doublons normalisés et les fuites d'identifiants de scénario.

Ces données sont un jeu de démarrage très limité. Elles permettent de tester le pipeline, mais pas de revendiquer une compréhension robuste. La prochaine montée en qualité doit prioriser des exemples humains vérifiés, des cas difficiles et des tests de régression plutôt que des paraphrases artificielles en masse.

## Pipeline de données

Chaque échange JSONL est encodé indépendamment sous la forme `BOS + Utilisateur + BOZER + EOS`. Les frontières d'échange ne disparaissent donc plus lors de la concaténation. Le même format de prompt est utilisé pendant l'évaluation. Le batch sampler couvre toutes les fenêtres légales, y compris la dernière.

## Entraînement recommandé

Installer la dépendance :

```bash
python -m pip install -r requirements-neural.txt
```

Valider les trois partitions avant tout entraînement :

```bash
python -m bozer_neural.validate_corpus
```

Lancer un premier entraînement contrôlé :

```bash
python -m bozer_neural.train --steps 600 --batch-size 8 --grad-accum-steps 2 --eval-interval 25 --eval-batches 8 --warmup-steps 20 --early-stopping-patience 5 --output artifacts/bozer-neural-v0.4.pt
```

Le pipeline applique un warmup linéaire, une décroissance cosine avec plancher de taux d'apprentissage, une accumulation de gradients, un clipping des gradients et une validation déterministe. Il enregistre un checkpoint courant, un checkpoint de meilleure validation et l'état de l'optimiseur pour reprendre l'entraînement.

Reprendre à partir du dernier checkpoint :

```bash
python -m bozer_neural.train --resume artifacts/bozer-neural-v0.4.pt --output artifacts/bozer-neural-v0.4-next.pt --steps 300
```

Évaluer le meilleur checkpoint sur le jeu de test indépendant :

```bash
python -m bozer_neural.evaluate --checkpoint artifacts/bozer-neural-v0.4.best.pt --data data/bozer_neural_v0_4/test_fr.jsonl --samples 36
```

Ne jamais choisir une version uniquement parce que la perte d'entraînement baisse. Comparer la perte de validation, les générations par catégorie, les erreurs de sécurité et les régressions face à la version précédente.

## Boucle d'apprentissage contrôlée

Voir `bozer_neural/LEARNING.md`. Les retours sont d'abord placés dans une file d'attente, puis validés par une personne avant d'être ajoutés au fichier d'entraînement. La validation et le test sont protégés. Après ajout, on lance une nouvelle session de fine-tuning en reprenant le checkpoint ; aucune conversation ne modifie automatiquement les poids.

## Limites et prochaines étapes

Le code du pipeline est maintenant plus complet, mais aucun checkpoint V0.4 entraîné n'a encore été validé dans cet environnement. Les exécutions GitHub Actions récentes sont marquées en échec avec zéro job retourné, ce qui bloque la confirmation automatisée. Le modèle n'est pas encore connecté à l'application Android.

Les prochaines étapes sont : résoudre l'exécution CI, lancer et inspecter un vrai entraînement, augmenter le corpus avec des exemples humains contrôlés, mesurer la qualité sur des prompts entièrement nouveaux, puis seulement envisager une intégration mobile.
