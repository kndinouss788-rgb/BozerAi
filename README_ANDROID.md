# BOZER Android — application de chat en validation

BOZER est une application Android Jetpack Compose installable. Cette version comprend le chat, une mémoire SQLite, un moteur local llama.cpp à télécharger à la demande, une recherche publique explicite limitée à Wikipédia, des actions Android avec confirmation, l'entrée vocale Android, la synthèse vocale Android et une bulle flottante activée par l'utilisateur.

## Modes de conversation

- Une nouvelle installation démarre en mode **En ligne** et utilise la passerelle HTTPS BOZER configurée par défaut dans la compilation. La valeur peut être remplacée par la propriété Gradle ou la variable d'environnement `BOZER_GATEWAY_URL`.
- Le mode **Automatique** conserve les conversations ordinaires sur le téléphone et utilise le modèle local installé.
- Le mode **Hors ligne** utilise uniquement le modèle local installé.
- Le choix explicite de mode est mémorisé. Une préférence déjà enregistrée n'est pas écrasée lors d'une mise à jour.
- Le modèle local n'est pas téléchargé automatiquement : il pèse environ 491 Mo et s'installe à la demande.
- Les clés des fournisseurs IA restent côté serveur ; aucune clé fournisseur n'est embarquée dans l'APK.

En mode En ligne, les messages récents de la conversation sont transmis à la passerelle BOZER pour obtenir une réponse. Choisis Automatique ou Hors ligne si tu ne souhaites pas transmettre les conversations au serveur.

## Cerveau local

- Modèle : Qwen2.5-0.5B-Instruct GGUF, quantification Q4_K_M.
- Taille : 491 400 032 octets (environ 491 Mo).
- Téléchargement à la demande depuis une révision Hugging Face épinglée.
- SHA-256 attendu : `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.
- Stockage dans le répertoire privé de l'application (`filesDir/models`).
- Vérification SHA-256 avant l'installation et le chargement.
- Inférence locale par llama.cpp/JNI, sans clé API et sans serveur requis.
- Contexte natif : 1 024 tokens ; génération limitée à 192 tokens par réponse.
- ABI compilées : `arm64-v8a` et `x86_64`.

Ce modèle de 0,5 milliard de paramètres est un modèle local de démarrage, pas un équivalent de ChatGPT. La qualité, la vitesse, la RAM, la chauffe et l'autonomie dépendent du téléphone. Aucun résultat d'émulateur ne prouve les performances sur un appareil physique.

## Conversation et mémoire

L'historique est conservé dans SQLite. Les faits explicitement demandés sont stockés séparément et récupérés en fonction de la question. L'application permet de corriger ou d'oublier un fait, d'effacer l'historique et d'effacer les mémoires. Le prompt est structuré en ChatML et neutralise les marqueurs fournis par des données non fiables.

## Recherche Internet

Une demande explicite commençant par « Recherche sur Internet » déclenche une requête réelle vers l'API de recherche de Wikipédia en français depuis Android. Les titres, extraits et URL sont ajoutés au contexte comme données non fiables et les liens sources sont ajoutés à la réponse. La recherche Android est actuellement limitée à Wikipédia : ce n'est pas encore un moteur général multi-sources. Les conversations ordinaires ne déclenchent pas de recherche.

## Actions Android

Les intentions prises en charge sont :
- ouvrir une application installée ;
- ouvrir Horloge pour préparer un réveil ;
- ouvrir le composeur téléphonique ;
- ouvrir l'application SMS avec un brouillon prérempli.

Une confirmation est requise avant de lancer une intention. BOZER ne passe pas d'appel et n'envoie pas de SMS automatiquement. Le réveil doit être vérifié et confirmé dans l'application Horloge.

## Voix et bulle flottante

L'entrée vocale demande l'autorisation microphone lorsque l'utilisateur appuie sur le bouton. Elle utilise le service de reconnaissance vocale configuré sur Android et demande la reconnaissance hors ligne lorsqu'elle est disponible ; le fournisseur peut ignorer cette préférence. BOZER ne conserve pas d'enregistrement audio. La sortie vocale utilise Android TextToSpeech et dépend des voix installées. La sortie vocale est activée par défaut sur une installation neuve ; un choix explicite de l'utilisateur est conservé.

Il n'existe pas encore de mot de réveil permanent (« BOZER » en écoute continue). La bulle flottante est un service de premier plan activé volontairement après autorisation d'affichage par-dessus les autres applications. Elle peut être déplacée et ouvre l'activité principale ; elle n'écoute pas le microphone et n'exécute aucune action téléphonique.

## État des grands modules

Le dépôt contient des briques de fondation pour l'intention, la planification d'actions, la recherche et le dialogue. Les sept modules métier BOZER BUSINESS, AGENT, RESEARCH, DOCUMENTS, SECRETARY, TRANSLATOR et DEVELOPER ne sont pas encore tous intégrés comme fonctions Android complètes. En particulier, la recherche Android actuelle est limitée à Wikipédia ; la génération PDF, les flux métier complets, les rappels planifiés et la traduction vocale dédiée ne sont pas certifiés dans cette application.

## Construire et tester

Prérequis : JDK 17, Android SDK, Android NDK `27.2.12479018`, CMake `3.22.1` et Gradle 8.9.

```bash
python -m unittest discover -s tests -v
gradle :app:assembleDebug --no-daemon
gradle :app:connectedDebugAndroidTest --no-daemon
```

GitHub Actions comporte des workflows pour les tests du noyau Python, la compilation de l'APK Android et les scénarios instrumentés sur émulateur avec le vrai modèle. L'artefact `bozer-debug-apk` est signé avec la clé de débogage Android : il convient aux essais, pas à une publication Play Store. Les artefacts CI sont conservés 14 jours.

## Limites de validation

Une compilation réussie ou des tests Python réussis ne suffisent pas à déclarer BOZER complet. Une vérification instrumentée avec le vrai modèle et un essai physique sur un téléphone Android 13 restent nécessaires pour mesurer les performances, vérifier les services vocaux, la bulle, les restrictions du fabricant et la consommation de batterie.
