# BOZER Android — moteur local et application

Cette documentation décrit les capacités présentes dans le module Android et les vérifications qui restent nécessaires. Le moteur local est intégré au projet ; les performances sur un téléphone réel doivent encore être mesurées.

## Fonctions présentes

- interface de chat Jetpack Compose ;
- historique et mémoire de faits stockés localement dans SQLite ;
- runtime natif llama.cpp appelé depuis Kotlin via JNI ;
- modèle Qwen2.5-0.5B-Instruct GGUF téléchargé à la demande ;
- stockage dans le répertoire privé de l'application ;
- vérification SHA-256 avant chargement du modèle ;
- modes de conversation Automatique, En ligne et Hors ligne ;
- passerelle BOZER distante et moteur local selon le mode, la disponibilité et la configuration ;
- reconnaissance vocale déclenchée par l'utilisateur via RecognizerIntent ;
- synthèse vocale via Android TextToSpeech.

## Modèle local

- Modèle : Qwen2.5-0.5B-Instruct, quantification Q4_K_M.
- Taille documentée : environ 491 Mo.
- Révision Hugging Face épinglée dans le code.
- Empreinte SHA-256 : `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.

Le téléchargement initial nécessite Internet. Le modèle est ensuite conservé dans le stockage privé de l'application. Le fichier temporaire de téléchargement n'est pas chargé comme modèle et doit être supprimé si le téléchargement ou la vérification échoue.

## Runtime natif

Le module CMake récupère llama.cpp à une révision Git immuable et compile une bibliothèque JNI `bozer_llama` pour `arm64-v8a` et `x86_64`. Le moteur charge le modèle GGUF depuis le stockage privé, avec un contexte configuré à 2048 tokens et une génération plafonnée à 256 tokens.

Le modèle est compact. Il ne faut pas le présenter comme un cerveau de niveau ChatGPT ni supposer qu'il répondra en deux secondes sur tous les téléphones. Le temps de réponse, la qualité, la RAM, la batterie et la chauffe doivent être mesurés sur les appareils ciblés.

## Voix : limite actuelle

Le bouton vocal lance Android RecognizerIntent, qui délègue la reconnaissance vocale à un service Android. La sortie audio passe par TextToSpeech. Cela ne constitue pas un écouteur permanent, un mot-clé local « BOZER » ou un assistant système sélectionné.

Le manifeste actuel n'a pas encore de VoiceInteractionService BOZER. Android contrôle les permissions micro, les rôles système et les indicateurs de confidentialité ; le code BOZER ne peut pas les contourner.

## Actions actuellement prises en charge

Le planificateur Android comprend un périmètre limité :
- ouverture d'applications ;
- ouverture de l'application Horloge avec l'heure demandée ;
- ouverture du composeur avec un numéro prérempli ;
- ouverture de l'application SMS avec un brouillon prérempli.

Les appels et SMS ne sont pas exécutés automatiquement. L'utilisateur doit terminer les opérations dans l'application concernée. Ce périmètre ne constitue pas un contrôle général du téléphone.

## Compilation et tests

Le workflow GitHub Actions `BOZER Android APK build` compile l'APK debug avec le SDK, le NDK et CMake configurés. L'artefact de CI est un build de test, pas une version de production signée.

Commandes Android Studio/Gradle :
```bash
gradle :app:assembleDebug --no-daemon
gradle :app:connectedDebugAndroidTest
```

Les workflows distincts du dépôt exécutent :
1. tests Python du noyau ;
2. compilation APK Android ;
3. tests instrumentés sur émulateur avec le moteur local.

Un build APK vert ne prouve pas une installation fonctionnelle sur un téléphone réel. Avant une publication, il faut installer l'APK sur les appareils cibles, télécharger le modèle, réaliser plusieurs conversations et actions, puis mesurer vitesse et stabilité.

## Points de validation restants

- reproduire et diagnostiquer tout crash à l'envoi de message signalé sur une ancienne version installée ;
- valider le téléchargement, le chargement et la génération du modèle sur des téléphones réels ;
- mesurer la vitesse, la RAM, la batterie et la chaleur ;
- tester modes en ligne/hors ligne et pertes de réseau ;
- implémenter et tester une vraie architecture VoiceInteractionService avant de parler d'assistant système ou de mot-clé permanent.

Voir [docs/BOZER_MASTER_PLAN.md](docs/BOZER_MASTER_PLAN.md) pour les 10 étapes et [docs/BOZER_STAGE1_BASELINE.md](docs/BOZER_STAGE1_BASELINE.md) pour l'audit détaillé.
