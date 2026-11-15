# BOZER — registre des modules codés avant intégration

Mis à jour le 15 novembre 2026. Dépôt : branche `bozer-neural-v0.1`.

Ce registre décrit les modules logiciels présents, leur rôle et les dépendances qui ne peuvent pas être remplacées par du code seul. Les modules restent séparés jusqu'à la phase d'intégration demandée.

## 1. BOZER NEURAL

| Module | Fichier | Rôle |
|---|---|---|
| Configuration 1B | `bozer_neural/config_1b.py` | Architecture cible, contraintes de dimensions et comptage des paramètres |
| Transformer 1B | `bozer_neural/model_1b.py` | Embeddings, RoPE, GQA, RMSNorm, SwiGLU, loss et génération de base |
| Tokenizer | `bozer_neural/tokenizer.py` | Encodage UTF-8 et BPE entraîné sur corpus fourni |
| Entraînement | `bozer_neural/train_1b.py` | Optimiseur, précision, checkpoint, reprise, warmup et arrêt anticipé |
| Génération contrôlée | `bozer_neural/generation.py` | Top-k, top-p, température, répétition, n-grammes et tokens d'arrêt |
| Runtime checkpoint | `bozer_neural/runtime.py` | Chargement strict des poids et génération uniquement depuis un checkpoint valide |
| Mémoire | `bozer_neural/memory.py` | Provenance, niveau de confidentialité, durée de vie, oubli et purge |
| Planification | `bozer_neural/planning.py` | Actions typées, capacités requises et confirmation des actions sensibles |
| Routage de langue | `bozer_neural/language/language_router.py` | Choix explicite fr/en/mos; abstention lorsque l'auto-détection n'est pas fiable |
| Préparation parallèle | `bozer_neural/language/parallel_corpus.py` | Validation des paires traduites, droits, séparation des sources et export JSONL |
| Évaluation de traduction | `bozer_neural/language/evaluation.py` | Exactitude, couverture et ambiguïté sur jeux de référence |
| Génération de pipeline | `bozer_neural/learning.py`, `train_1b.py` | Infrastructure d'apprentissage et d'évaluation déjà présente |

**Limite importante :** l'architecture cible de 1 115 785 216 paramètres reste une architecture initialisée aléatoirement tant qu'un checkpoint entraîné n'est pas produit. Les outils de runtime ne créent pas de connaissances par eux-mêmes.

## 2. BOZER TTS

| Module | Fichier | Rôle |
|---|---|---|
| Profil d'identité vocal | `bozer_neural/voice/voice_profile.json` | Identité masculine adulte, médium-grave chaleureuse, style original |
| Contrat TTS | `bozer_neural/voice/tts_contract.py` | Requête, chunks PCM, backend interchangeable et interruption |
| Prononciation | `bozer_neural/voice/pronunciation.py` | Normalisation Unicode sans supprimer les diacritiques |
| Frontend texte | `bozer_neural/voice/frontend.py` | Segmentation, longueur des blocs et pauses suivant la ponctuation |
| Lexique phonétique | `bozer_neural/voice/pronunciation_lexicon.py` | Prononciations candidates/validées et recherche contrôlée |
| Qualité audio | `bozer_neural/voice/quality.py` | Mesures PCM : niveau crête, RMS, saturation et silence |
| Corpus et validation | `bozer_neural/voice/validate_voice_corpus.py` | Consentement déclaré, format WAV, sessions et séparation des partitions |

**Limite importante :** aucun checkpoint vocal BOZER n'est présent. Le moteur sherpa-onnx reste un candidat runtime; ses poids de voix et leurs licences doivent être examinés séparément. Les fichiers de contrats ne synthétisent pas encore de son.

## 3. Dictionnaire et traducteur Mooré ↔ Français

| Module | Fichier | Rôle |
|---|---|---|
| Traduction exacte | `bozer_neural/language/moore_french.py` | Aller-retour fr/mos, ambiguïtés explicites, aucune traduction inventée |
| Recherche dictionnaire | `bozer_neural/language/dictionary.py` | Recherche exacte et partielle, filtres et mode candidat facultatif |
| Règles morphologiques | `bozer_neural/language/morphology.py` | Moteur déclaratif de préfixes/suffixes, règles uniquement si documentées |
| Gouvernance | `bozer_neural/language/governance.py` | Passage candidate → validated après droits clairs et revue native |
| Validation de lexique | `bozer_neural/language/validate_lexicon.py` | Schéma, identifiants, statuts, doublons et provenance |
| Audit import | `bozer_neural/language/audit_malm_candidates.py` | Audit des propositions importées et file de relecture |
| Registre morphologique | `data/moore_french/morphology_rules.json` | Emplacement de règles documentées; vide tant qu'aucune règle n'est confirmée |
| Prononciation Mooré | `data/moore_french/pronunciation_lexicon.json` | Emplacement de prononciations relues; vide pour éviter les suppositions |

**Limite importante :** les milliers de propositions importées ne sont pas des traductions validées. Les droits sous-jacents de certaines sources restent à confirmer et le dictionnaire de production doit continuer à n'exposer que les entrées validées.

## 4. Tests et garde-fous ajoutés

- `tests/test_bozer_workstream_modules.py` : mémoire, planification, langue, TTS et refus de traduction non trouvée.
- `tests/test_bozer_extended_modules.py` : génération, runtime, dictionnaire, morphologie, gouvernance, préparation de corpus et qualité audio.
- `.github/workflows/bozer-workstream-modules.yml` : exécution CI des tests autonomes.
- `.github/workflows/bozer-neural-1b-checks.yml` : compile, validation du corpus, tests NEURAL et entraînement de baseline.

## 5. Éléments non codables sans ressource externe

Ces éléments sont explicitement reportés; aucun faux livrable ne les remplace :

1. Poids NEURAL appris : nécessite des corpus autorisés, assez volumineux, et un entraînement avec calcul disponible.
2. Compréhension fiable du Mooré : nécessite des phrases, variantes et exemples validés par des locuteurs natifs.
3. Traduction de phrases Mooré : nécessite grammaire, morphologie et corpus parallèles vérifiés; l'assemblage mot à mot est désactivé.
4. Voix BOZER finale : nécessite un locuteur adulte consentant, ses enregistrements, un modèle/runtime choisi et une phase d'entraînement.
5. Prononciation/tons Mooré : nécessite des relectures et enregistrements validés; aucune valeur phonétique n'est devinée.
6. Validation finale Android : nécessite intégration, compilation et essais sur appareils physiques.

## 6. Ordre de travail retenu

1. Finir les modules autonomes qui ne dépendent pas de ces ressources.
2. Faire passer leurs tests et corriger les régressions.
3. Faire la revue des contrats et des dépendances.
4. Intégrer seulement après la fin de cette phase.
5. Vérifier l'ensemble sur CI puis sur téléphone.

Ce registre n'affirme pas que BOZER NEURAL, la voix TTS ou le traducteur complet sont déjà opérationnels.
