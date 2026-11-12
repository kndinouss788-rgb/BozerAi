# BOZER AI

BOZER est un assistant Android et un noyau décisionnel Python conçus pour transformer des intentions en réponses et en actions préparées, avec permissions, confirmations et vérification.

## Documentation du projet

- **Plan directeur complet en 10 étapes :** [docs/BOZER_MASTER_PLAN.md](docs/BOZER_MASTER_PLAN.md)
- **Audit et état de référence de l'étape 1 :** [docs/BOZER_STAGE1_BASELINE.md](docs/BOZER_STAGE1_BASELINE.md)
- **Feuille de route plateforme Android, VoiceInteractionService et intégration OEM :** [docs/BOZER_PLATFORM_ROADMAP.md](docs/BOZER_PLATFORM_ROADMAP.md)
- **Guide technique Android :** [README_ANDROID.md](README_ANDROID.md)

## Composants du dépôt

### Application Android

Le module Android est une application Kotlin/Jetpack Compose (minSdk 26, compile/target SDK 35, Java 17). Il comprend notamment :

- interface de chat et historique SQLite local ;
- intégration llama.cpp par JNI avec moteur local GGUF ;
- téléchargement à la demande du modèle Qwen2.5-0.5B-Instruct Q4_K_M dans le stockage privé, avec vérification SHA-256 ;
- ChatRepository avec modes Automatique, En ligne et Hors ligne ;
- RemoteBrainClient vers la passerelle BOZER ; les clés des fournisseurs IA doivent rester côté serveur ;
- saisie vocale déclenchée par l'utilisateur via Android RecognizerIntent ;
- sortie vocale via Android TextToSpeech ;
- actions actuellement limitées à ouvrir une application, préparer un réveil dans Horloge, ouvrir le composeur téléphonique ou préparer un brouillon SMS.

La saisie vocale actuelle n'est pas un mot-clé permanent. Le dépôt ne déclare pas encore de VoiceInteractionService BOZER. Les actions actuelles ne permettent pas de contrôler toutes les applications : un appel n'est pas passé automatiquement, un SMS n'est pas envoyé automatiquement et un réveil reste à confirmer dans Horloge.

### Noyau Python

Le paquet `bozer_brain` contient notamment :
- parsing d'intentions, dialogue et contexte ;
- propositions d'action, planification multi-étapes et règles de sécurité ;
- mémoire locale et registre d'outils ;
- EYE_SEARCH avec sources publiques ;
- passerelle de modèle avec contrôles explicites d'activation.

Les appels au modèle et à la recherche ne doivent pas être activés implicitement. Les résultats web sont des données non fiables et ne sont jamais des instructions à exécuter.

## Fournisseur IA et passerelle

Le noyau Python inclut un adaptateur OpenAI Responses API et un adaptateur compatible avec des endpoints `/chat/completions`.

Configuration côté serveur uniquement :
- `BOZER_MODEL_API_KEY` : clé secrète ;
- `BOZER_MODEL_NAME` : identifiant du modèle autorisé ;
- `BOZER_MODEL_BASE_URL` : URL de base facultative ;
- `BOZER_MODEL_TIMEOUT` : délai maximal facultatif ;
- `BOZER_MODEL_API_STYLE` : `responses` par défaut ou `chat_completions`.

Ne jamais placer de clé fournisseur dans les sources Android, ressources, journaux, dépôt ou APK. Un appel à un fournisseur peut engendrer un coût : protéger la passerelle, appliquer des limites et informer l'utilisateur.

## Modèle local Android

Le guide [README_ANDROID.md](README_ANDROID.md) décrit le téléchargement et l'intégration du modèle local. Le modèle compact est un point de départ, pas une garantie de qualité ou de vitesse équivalente à un grand modèle distant. Les performances doivent être mesurées sur chaque catégorie d'appareil cible.

## Tests et compilation

Noyau Python :
```bash
python -m unittest discover -s tests -v
```

Compilation APK Android (Java 17, Android SDK, NDK 27.2.12479018 et CMake 3.22.1) :
```bash
gradle :app:assembleDebug --no-daemon
```

Les workflows GitHub Actions du dépôt séparent tests du noyau Python, compilation APK Android et tests Android sur émulateur. Une compilation réussie prouve que l'APK est compilable ; elle ne prouve pas que toutes les fonctions marchent sur un téléphone réel.

## Principes de sécurité

- Android garde la maîtrise des autorisations et écrans système.
- BOZER ne contourne pas les permissions et ne masque pas l'indicateur micro.
- Les actions sensibles nécessitent une confirmation adaptée.
- Les erreurs et refus Android doivent être rapportés honnêtement.
- L'interface approuvée et le cœur conversationnel sont protégés des modifications non autorisées.

## Statut produit

Ce dépôt est un projet en développement. Il ne faut pas présenter le contrôle vocal complet, le réveil permanent, l'assistant système, le Guardian, tous les connecteurs ou l'intégration OEM comme déjà livrés. Consulter le plan directeur et le rapport d'audit pour le statut vérifié.
