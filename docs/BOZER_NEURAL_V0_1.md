# BOZER NEURAL — état technique V0.5 / architecture cible 1B

## Résumé honnête

Le dépôt contient maintenant :
- un petit Transformer français entraîné depuis une initialisation aléatoire ;
- un tokenizer byte-level BPE appris uniquement sur la partition d'entraînement ;
- un pipeline de dialogues supervisés avec masquage de la perte sur les réponses de BOZER ;
- une validation séparée, un test indépendant et un audit des chevauchements ;
- une architecture cible 1B codée et testée avec de petites configurations ;
- un rapport de baseline et des checkpoints PyTorch versionnés.

Aucun poids pré-entraîné n'est chargé. Aucun service API distant ne remplace le cerveau. Le modèle n'est pas encore intégré à l'application Android.

## Petit modèle effectivement entraîné

`BozerTransformer` est un Transformer causal décodeur :
- 2 blocs, 4 têtes d'attention causale ;
- dimension cachée 64, contexte de 128 tokens ;
- embeddings de tokens et positions apprises ;
- LayerNorm, MLP GELU et projection de sortie liée aux embeddings d'entrée ;
- 141 056 paramètres entraînables avec le vocabulaire BPE de 512 tokens.

Le tokenizer de base représente les octets UTF-8 et les tokens spéciaux (259 identifiants). Le BPE ajoute 253 fusions apprises depuis le seul fichier train, pour un vocabulaire de 512. Le tokenizer est sérialisé avec les checkpoints afin que l'encodage reste identique à l'inférence.

## Modèle cible 1B codé, non entraîné

`BozerNeural1B` utilise :
- 24 blocs, dimension 2 048, contexte 2 048 ;
- 16 têtes Q et 4 têtes KV (GQA) ;
- RoPE, RMSNorm et MLP SwiGLU ;
- embeddings d'entrée et projection de sortie liés ;
- 16 384 identifiants de vocabulaire ;
- exactement 1 115 785 216 paramètres dans la configuration cible.

Le modèle 1B n'a pas été entraîné. Les tests utilisent une petite configuration pour vérifier les formes, l'attention causale, le gradient, la génération, le format de checkpoint et une étape d'entraînement. Ils ne prouvent pas que le modèle complet peut être entraîné sur le matériel disponible.

## Corpus conversationnel français V0.5

Dossier : `data/bozer_neural_v0_5/`.
- 263 exemples d'entraînement ;
- 60 exemples de validation ;
- 60 exemples de test indépendant ;
- six catégories équilibrées, avec des scénarios multi-tours.

Les exemples sont synthétiques et ciblés. Ils permettent de tester le comportement et le pipeline, mais ne constituent pas un corpus général de pré-entraînement. Les partitions validation/test restent hors entraînement.

## Pipeline et entraînement

Chaque dialogue est encodé avec ses frontières BOS/EOS. La fonction de perte porte uniquement sur la réponse BOZER et EOS, pas sur la demande utilisateur. L'échantillonnage équilibre les catégories. Le pipeline comprend AdamW, warmup, décroissance cosinus, accumulation de gradients, clipping, validation par catégorie, arrêt anticipé, reprise et sauvegarde atomique.

Commande de référence utilisée :

```bash
python -m bozer_neural.validate_corpus \
  --train data/bozer_neural_v0_5/train_fr.jsonl \
  --validation data/bozer_neural_v0_5/validation_fr.jsonl \
  --test data/bozer_neural_v0_5/test_fr.jsonl

python -m bozer_neural.train \
  --data data/bozer_neural_v0_5/train_fr.jsonl \
  --validation-data data/bozer_neural_v0_5/validation_fr.jsonl \
  --steps 600 --batch-size 8 --learning-rate 3e-4 \
  --eval-interval 25 --eval-batches 4 \
  --tokenizer-vocab-size 512 --grad-accum-steps 2 \
  --warmup-steps 20 --early-stopping-patience 5 \
  --output artifacts/bozer-neural-v0.4-corpus-v0.5.pt

python -m bozer_neural.evaluate \
  --checkpoint artifacts/bozer-neural-v0.4-corpus-v0.5.best.pt \
  --data data/bozer_neural_v0_5/test_fr.jsonl \
  --batches 12 --samples 60 --max-new-tokens 96 \
  --report artifacts/bozer-neural-corpus-v0.5-test-report.json
```

## Résultats réellement mesurés — 15 novembre 2026

Workflow réussi : [run 43701951456](https://github.com/kndinouss788-rgb/BozerAi/actions/runs/43701951456), commit `4724a2228d95cf903f534546ecbac163c271f39b`.

| Mesure | Résultat |
|---|---:|
| Paramètres du petit modèle | 141 056 |
| Étapes d'entraînement CPU | 600 |
| Perte entraînement début → fin | 6,2467 → 4,2070 |
| Meilleure perte validation | 4,2717 |
| Perte test indépendant | 4,3086 |
| Exemples générés sur test | 60 |
| Correspondances exactes normalisées | 0/60 |
| Sorties non vides | 58/60 |

Les six catégories obtiennent chacune 0/10 correspondance exacte. La baisse de perte montre que l'optimiseur apprend des régularités du petit corpus. Les générations restent cependant fragmentaires ou fausses : **aucune compétence conversationnelle utile n'est démontrée**.

L'évaluateur rapporte maintenant la correspondance exacte normalisée (Unicode NFKC, casse, ponctuation et espaces) et le taux de sorties non vides, globalement et par catégorie. Ce score n'est pas un score sémantique ; il donne un contrôle strict et reproductible.

## Tests vérifiés

Sur le commit indiqué :
- compilation Python des modules et tests : succès ;
- validation des partitions V0.5 : succès ;
- 20 tests `unittest` : succès ;
- 39 tests `pytest` : succès ;
- entraînement de 600 étapes depuis des poids aléatoires : succès ;
- évaluation des 60 exemples de test : succès ;
- artefact contenant les checkpoints et le rapport JSON : publié.

Quatre avertissements non bloquants ont été signalés, notamment NumPy absent de l'environnement CI et une API GradScaler dépréciée.

## Format des poids

Les checkpoints `.pt` utilisent le format `bozer-neural-v0.4` et contiennent la configuration, le tokenizer sérialisé, `model_state_dict`, les métriques et, pour le checkpoint de reprise, l'état de l'optimiseur. Le meilleur checkpoint est conservé séparément. Ce format sert à l'entraînement Python ; ce n'est pas encore un format de distribution Android ni un format quantifié.

## Données générales de pré-entraînement : blocage réel

Le dossier `data/bozer_pretraining_v1/` ne contient pas de corpus brut. Le pipeline de préparation existe, mais aucun texte externe n'a été importé. L'audit des sources distingue les candidats et les droits à confirmer. Le corpus conversationnel V0.5 ne remplace pas un corpus de pré-entraînement général.

Le modèle cible 1B demanderait au minimum théorique FP32 environ 17,85 Go pour poids, gradients et deux moments AdamW, avant activations et buffers. Le repère de planification d'environ 20 tokens par paramètre conduirait à 22,3 milliards de tokens ; c'est une heuristique, pas une exigence universelle. Le runner CPU gratuit sert aux tests et au petit modèle, pas au pré-entraînement complet 1B.

## Prochaines étapes

1. Constituer un pilote de 100 000 à 1 million de tokens français depuis des sources dont les droits sont documentés, sans mélanger les documents entre partitions.
2. Mesurer provenance, qualité, déduplication, couverture et taille en tokens avant toute longue session.
3. Augmenter le jeu de dialogues supervisés avec des exemples humains vérifiés et des cas difficiles, pas seulement des paraphrases synthétiques.
4. Ajouter des critères de réussite propres à chaque tâche et conserver des tests jamais vus.
5. Mesurer un modèle intermédiaire avant d'envisager l'entraînement du 1B sur un vrai GPU.
6. Définir et tester un format d'export mobile séparé avant toute intégration Android.

La PR #6 reste ouverte en brouillon et n'a pas été fusionnée. Aucun fichier Android ni secret n'a été modifié.
