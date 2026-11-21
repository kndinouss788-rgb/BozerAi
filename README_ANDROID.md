# BOZER Android — cerveau local pré-entraîné

BOZER utilise désormais un modèle de langage déjà pré-entraîné comme cerveau de conversation principal :

- **Modèle :** Qwen2.5-0.5B-Instruct (GGUF, quantification Q4_K_M).
- **Licence du modèle :** Apache-2.0.
- **Taille du fichier :** 491 400 032 octets (environ 491 Mo).
- **Exécution :** llama.cpp natif via JNI, directement sur Android.
- **Coût d'inférence :** aucun appel API nécessaire pour les conversations ordinaires.
- **Intégration :** modèle téléchargé et vérifié pendant la compilation, puis embarqué dans les ressources de l'APK ; copie initiale automatique vers le stockage privé, sans bouton ni téléchargement réseau dans l'application.
- **Intégrité :** SHA-256 contrôlé avant chargement.

Le modèle est la base linguistique pré-entraînée. BOZER ajoute sa propre identité, son ton, sa mémoire locale, ses règles de fiabilité et son système d'actions Android autour de ce modèle. En mode Automatique, BOZER utilise la passerelle distante configurée lorsqu'Internet est disponible, puis revient au cerveau local si le service distant échoue. Sans connexion, le modèle intégré répond localement. Le mode Hors ligne interdit les appels réseau.

## Pourquoi ce modèle

Qwen2.5-0.5B-Instruct est choisi pour son format GGUF, sa licence Apache-2.0, son faible encombrement et sa compatibilité avec le moteur llama.cpp déjà présent dans le projet. Il est adapté à un premier déploiement sur téléphones Android modestes. Ce n'est pas un modèle de niveau ChatGPT : sa qualité, sa vitesse et sa consommation RAM doivent être mesurées sur les appareils réels.

## Moteur natif

Le module CMake récupère llama.cpp à une révision Git immuable et compile `bozer_llama` pour `arm64-v8a` et `x86_64`. Le modèle est chargé depuis le stockage privé de l'application. Le prompt ChatML applique l'identité et les règles BOZER.

## État de validation

- La compilation Gradle récupère le modèle depuis une révision Hugging Face figée, vérifie sa taille et son SHA-256, puis l'intègre aux assets de l'APK.
- Au premier démarrage, BOZER copie automatiquement le modèle depuis l'APK vers son stockage privé, vérifie son intégrité puis le charge ; aucun bouton de téléchargement n'est exposé.
- Le modèle augmente l'APK d'environ 491 Mo ; la première préparation demande aussi un espace libre temporaire suffisant sur le téléphone.
- En mode Automatique, la passerelle BOZER configurée est utilisée avec Internet, avec repli sur le modèle local ; sans réseau, la conversation reste locale.
- **Aucune validation réelle de génération sur le TECNO de l'utilisateur n'est revendiquée.**
- Ces changements sont commis sur `bozer-neural-v0.1`, mais la compilation Android/APK, la vérification de l'asset dans l'APK et le test sur téléphone restent à effectuer.

Le chantier expérimental d'entraînement du modèle BOZER NEURAL 1B est arrêté. Son code et ses données sont conservés dans l'historique Git pour archivage, mais ils ne sont plus la voie active du cerveau Android.
