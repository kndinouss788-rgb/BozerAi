# BOZER Android — application de chat locale

BOZER est une application Android en cours de validation. Le dépôt contient une application Jetpack Compose installable, un moteur de langage local natif, une mémoire SQLite, une recherche publique déclenchée explicitement, des actions Android confirmées, une entrée vocale via les services Android et une bulle flottante activée par l'utilisateur.

## Cerveau local

- Modèle : Qwen2.5-0.5B-Instruct GGUF, quantification Q4_K_M.
- Taille : 491 400 032 octets (environ 491 Mo).
- Téléchargement à la demande depuis une révision Hugging Face épinglée.
- SHA-256 attendu : `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.
- Stockage dans le répertoire privé de l'application (`filesDir/models`).
- Vérification SHA-256 avant l'installation et le chargement ; le fichier temporaire n'est jamais utilisé comme modèle.
- Inférence locale par llama.cpp/JNI, sans clé API et sans serveur requis.
- Contexte natif : 1 024 tokens ; génération limitée à 192 tokens par réponse.
- ABI compilées : `arm64-v8a` et `x86_64`.

Ce modèle de 0,5 milliard de paramètres est un modèle local de démarrage, pas un équivalent de ChatGPT. La qualité, la vitesse, la RAM, la chauffe et l'autonomie dépendent du téléphone. Aucun résultat d'émulateur ne prouve les performances sur un appareil physique.

## Conversation et mémoire

L'historique est conservé dans SQLite. Les faits explicitement demandés sont stockés séparément et récupérés en fonction de la question. L'application permet de corriger ou d'oublier un fait, d'effacer l'historique et d'effacer les mémoires. La migration de la version 1 vers la version 2 conserve les messages existants.

Le prompt est structuré en ChatML. Les messages, faits et extraits externes sont échappés avant insertion afin d'éviter qu'un texte non fiable soit interprété comme une nouvelle instruction structurelle.

## Recherche Internet

Une demande explicite commençant par « Recherche sur Internet » déclenche une requête réseau réelle vers l'API de recherche de Wikipédia en français depuis le processus Android. Les titres, extraits et URL récupérés sont intégrés au contexte comme données non fiables et les liens sources sont ajoutés à la réponse. La recherche est actuellement limitée à Wikipédia ; elle ne constitue pas encore un moteur général multi-sources.

En mode Hors ligne, ou sans connexion validée, aucune recherche n'est exécutée et l'application le signale. Les conversations ordinaires ne déclenchent pas de recherche.

## Actions Android

Les intentions prises en charge sont :
- ouvrir une application installée ;
- ouvrir Horloge pour préparer un réveil ;
- ouvrir le composeur téléphonique ;
- ouvrir l'application SMS avec un brouillon prérempli.

Une confirmation est requise avant de lancer une intention. BOZER ne passe pas d'appel et n'envoie pas de SMS automatiquement. Le réveil doit être vérifié et confirmé dans l'application Horloge.

## Voix et bulle flottante

L'entrée vocale demande l'autorisation microphone au moment où l'utilisateur appuie sur le bouton. Elle utilise le service de reconnaissance vocale configuré sur Android et demande la reconnaissance hors ligne lorsqu'elle est disponible. Le fournisseur Android peut ignorer cette préférence et appliquer ses propres règles réseau et confidentialité. BOZER ne conserve pas d'enregistrement audio. La sortie vocale utilise Android TextToSpeech et dépend des voix installées.

Il n'existe pas encore de mot de réveil permanent (« BOZER » en écoute continue). La bulle flottante est un service de premier plan activé volontairement après l'autorisation d'affichage par-dessus les autres applications. Elle peut être déplacée et ouvre l'activité principale ; elle n'écoute pas le microphone et n'exécute aucune action téléphonique.

## Confidentialité et réseau

- En mode Automatique ou Hors ligne, l'inférence conversationnelle reste sur le téléphone.
- Le mode En ligne n'est disponible que si une URL de passerelle HTTPS a été configurée à la compilation et si l'utilisateur sélectionne explicitement ce mode.
- Aucun secret ni aucune clé API ne doit être placé dans l'APK, les ressources ou le dépôt.
- Une recherche explicite contacte Wikipédia.
- La reconnaissance vocale dépend du fournisseur installé par l'utilisateur ; sa préférence hors ligne n'est pas une garantie du système.

## Construire et tester

Prérequis : JDK 17, Android SDK, Android NDK `27.2.12479018`, CMake `3.22.1` et Gradle 8.9.

```bash
# Tests du noyau Python
python -m unittest discover -s tests -v

# APK de débogage installable
gradle :app:assembleDebug --no-daemon

# Tests Android instrumentés sur un émulateur connecté
gradle :app:connectedDebugAndroidTest --no-daemon
```

GitHub Actions comporte trois workflows pertinents :
- `BOZER core tests` : tests Python ;
- `BOZER Android APK build` : compilation native de l'APK, vérification de sa présence et calcul SHA-256 ;
- `BOZER Android emulator and real-brain tests` : téléchargement et vérification du vrai GGUF, installation de l'APK, inférence multi-tours réelle, recherche réseau Android, persistance, actions, bulle et parcours micro/TTS, avec journaux et mesures de ressources.

L'APK de débogage est publié sous l'artefact `bozer-debug-apk` du workflow `BOZER Android APK build`. Les artefacts sont conservés 14 jours. Il est signé avec la clé de débogage Android : il convient aux essais, pas à une publication Play Store.

## Limites de validation

Une compilation réussie ou des tests Python réussis ne suffisent pas à déclarer BOZER complet. Le même commit `main` doit passer les workflows Android et les scénarios instrumentés avec le vrai modèle. Une vérification physique sur un téléphone Android 13 reste nécessaire pour mesurer les performances, vérifier les services vocaux installés, l'affichage de la bulle, les restrictions du fabricant et la consommation réelle de batterie.
