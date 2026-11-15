# BOZER NEURAL — rapport de référence du 15 novembre 2026

## Périmètre vérifié

- Dépôt : `kndinouss788-rgb/BozerAi`
- Branche : `bozer-neural-v0.1`
- Commit testé : `4ab658576ac9d60332a627f78f856ef1943af160`
- Workflow réussi : [BOZER NEURAL 1B architecture checks and baseline training — run 43699171904](https://github.com/kndinouss788-rgb/BozerAi/actions/runs/43699171904)
- Artefact : `bozer-neural-baseline-corpus-v0.5` (checkpoint courant, meilleur checkpoint et rapport JSON)

## Ce qui est codé

### Modèle de référence réellement entraîné

- Transformer causal décodeur, 2 blocs, 4 têtes, dimension 64, contexte de 128 tokens.
- Embeddings de tokens et de positions, attention causale, MLP GELU.
- Embedding d'entrée et projection de sortie liés.
- Tokenizer byte-level BPE de 512 identifiants, avec 253 fusions apprises uniquement depuis le fichier d'entraînement.
- Perte supervisée sur la réponse BOZER et son token EOS, pas sur le texte de la demande.
- Initialisation aléatoire ; aucun poids pré-entraîné chargé.

Avec le vocabulaire BPE de 512 tokens, le modèle de référence possède 141 056 paramètres entraînables uniques. La configuration byte de base à 259 tokens en possède 124 864.

### Modèle cible 1B codé, mais non entraîné

`BozerNeural1B` est une architecture séparée avec GQA (16 têtes Q / 4 têtes KV), RoPE, RMSNorm, SwiGLU, embeddings liés et contexte de 2 048 tokens. Sa configuration par défaut compte exactement 1 115 785 216 paramètres. Les poids sont initialisés aléatoirement.

Les tests de cette architecture utilisent de petites configurations : ils valident les formes, la causalité, la génération, le comptage des paramètres et une boucle d'entraînement miniature. Ils ne créent ni n'entraînent le modèle complet 1B.

## Données utilisées

Corpus conversationnel V0.5 :
- Entraînement : 263 exemples, 22 882 tokens encodés.
- Validation : 60 exemples, 5 233 tokens encodés.
- Test indépendant : 60 exemples, 5 495 tokens encodés.
- Six catégories : dialogue naturel, compréhension, raisonnement, planification, outils/vérification, sécurité/confidentialité/honnêteté.
- Les données sont synthétiques et organisées pour tester des comportements ciblés ; elles ne constituent pas un corpus général de pré-entraînement.

L'audit train/validation a relevé 311 demandes d'entraînement, 84 demandes de validation et zéro chevauchement. Le validateur contrôle également les doublons et les fuites d'historique entre partitions. Le jeu de test n'a pas été utilisé pour mettre à jour les poids.

## Entraînement effectivement réalisé

Le workflow a exécuté 600 étapes sur CPU, depuis une initialisation aléatoire, avec AdamW, taux d'apprentissage initial de (3\times10^{-4}), warmup, décroissance cosinus et accumulation de gradients.

| Mesure | Résultat |
|---|---:|
| Perte d'entraînement au début | 6,2467 |
| Perte d'entraînement à la fin | 4,1852 |
| Meilleure perte de validation | 4,2717 |
| Perte sur le test indépendant | 4,3086 |
| Tokens de test | 5 495 |
| Exemples générés et consignés | 60 |

La diminution de la perte montre que la boucle d'optimisation apprend des régularités du petit corpus. Elle ne prouve pas la compréhension, le raisonnement fiable ou la qualité d'un assistant.

## Résultat qualitatif sur les exemples jamais vus

Les 60 prompts de test ont été passés au modèle après entraînement. Les générations consignées sont généralement fragmentaires, incohérentes ou incorrectes. Exemples constatés dans le rapport :
- À « combien font 7 fois 8 ? », le modèle génère une suite de fragments et de nombres, pas « 56 ».
- À un calcul de reste de 4 000 F, il ne produit que « F. ».
- À une demande de planification, il produit parfois une seule lettre.

Aucun score sémantique fiable ni taux de réussite de tâche n'a été calculé. La conclusion honnête est donc : **pipeline d'entraînement démontré ; compétence conversationnelle utile non démontrée**.

## Tests et CI

Le run `43699171904` a terminé avec succès :
- compilation des modules Python ;
- validation des partitions V0.5 ;
- 19 tests `unittest` du pipeline de référence ;
- 39 tests `pytest`, dont architecture 1B, entraînement miniature, évaluation, pré-entraînement texte, tokenizer, traducteur Mooré–français et importeur de lexique ;
- entraînement CPU de 600 étapes du petit modèle ;
- évaluation des 60 exemples de test ;
- publication de l'artefact contenant les checkpoints et le rapport.

Quatre avertissements non bloquants ont été signalés pendant les tests, notamment l'absence de NumPy dans l'environnement et l'usage d'une API GradScaler dépréciée. Aucun de ces avertissements n'a fait échouer le run.

## Format des poids

Les checkpoints actuels sont des fichiers PyTorch `.pt` contenant :
- le nom de format/version ;
- la configuration du modèle ;
- le tokenizer sérialisé ;
- `model_state_dict` ;
- les métriques et métadonnées d'entraînement ;
- l'état de l'optimiseur dans le checkpoint de reprise.

Le meilleur checkpoint est enregistré sans état d'optimiseur. Ce format sert à l'entraînement et à la reprise Python ; ce n'est pas encore un format de distribution Android ni un format mobile quantifié. Un export de déploiement distinct devra être défini et testé avant intégration.

## Dimensionnement et décision

Le petit modèle de 141 056 paramètres est la première taille réellement entraînée dans les ressources CPU disponibles. Il convient uniquement à la validation de la chaîne données → tokenizer → entraînement → checkpoint → test. Il est beaucoup trop petit et trop peu entraîné pour être un assistant généraliste.

Le modèle cible 1B demande, au plancher théorique FP32, 17 852 563 456 octets pour les poids, les gradients et les deux moments AdamW, avant les activations, buffers et runtime. Le runner CPU gratuit n'est pas une cible réaliste pour son entraînement complet. Le corpus conversationnel V0.5 est également beaucoup trop petit pour ce modèle.

## Prochaine étape technique

1. Ne pas agrandir le modèle avant d'avoir agrandi et audité les données.
2. Construire un corpus de pré-entraînement français distinct des dialogues supervisés, à partir de sources dont les droits de réutilisation commerciale sont documentés.
3. Mesurer la taille en tokens, la déduplication, la qualité linguistique et la couverture des domaines.
4. Conserver des partitions validation/test indépendantes et enrichir les tests de tâches avec des critères de réussite explicites.
5. Poursuivre les expériences sur le petit modèle, puis mesurer un modèle intermédiaire avant de réserver le 1B à une infrastructure GPU adéquate.

Aucune modification Android, aucun secret, aucune fusion de PR et aucun appel à une API comme cerveau n'ont été nécessaires à cette étape.


## Besoins de pré-entraînement et sources

Le dossier data/bozer_pretraining_v1/ ne contient actuellement aucun fichier texte de pré-entraînement : seulement le README, l'outil de préparation et l'audit des sources. Le corpus V0.5 reste un jeu de dialogues supervisés, pas un corpus général.

L'audit de sources est dans [data/bozer_pretraining_v1/SOURCES_AND_RIGHTS.md](../data/bozer_pretraining_v1/SOURCES_AND_RIGHTS.md). Les débats de l'Assemblée nationale et les textes HAS sont des candidats, pas des sources déjà autorisées pour BOZER. Le premier a une fiche « Licence Ouverte » sans version explicite ; le second indique LO 2.0 mais signale des extractions PDF/XML parfois erronées ou obsolètes. Wikipédia exige une analyse des obligations CC BY-SA/GFDL et des contenus importés ; Common Crawl est exclu en l'absence de droits vérifiés au niveau des sites.

Pour donner un ordre de grandeur, la règle de planification Chinchilla d'environ 20 tokens par paramètre conduirait à environ 22,3 milliards de tokens de pré-entraînement pour 1 115 785 216 paramètres. C'est une heuristique, pas un minimum universel. Le corpus de dialogues V0.5 (22 882 tokens d'entraînement) ne doit pas être comparé directement à un corpus de pré-entraînement : il sert à l'ajustement supervisé et reste très petit. Référence : [Hoffmann et al., 2022](https://arxiv.org/abs/2203.15556).
