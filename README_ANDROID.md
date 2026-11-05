# BOZER Android — chat local avec llama.cpp

Cette version ajoute un vrai chemin d'inférence local dans le processus Android :

- interface de chat Jetpack Compose ;
- historique des messages conservé dans SQLite sur l'appareil ;
- runtime natif llama.cpp appelé depuis Kotlin par JNI ;
- modèle Qwen2.5-0.5B-Instruct GGUF téléchargé à la demande ;
- stockage du fichier dans `filesDir/models`, privé à l'application ;
- vérification SHA-256 avant de charger le modèle ;
- chargement au démarrage si le modèle est déjà installé ;
- réponses générées localement, sans clé API ni serveur distant.

## Modèle embarqué à la demande

- Modèle : Qwen2.5-0.5B-Instruct, quantification Q4_K_M.
- Taille annoncée : environ 491 Mo.
- Licence indiquée par l'éditeur : Apache-2.0.
- Révision Hugging Face épinglée dans le code pour rendre le téléchargement reproductible.
- Empreinte SHA-256 vérifiée : `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.

Le téléchargement nécessite Internet la première fois. Après installation, le modèle et l'inférence restent locaux. Le fichier temporaire `.part` n'est jamais chargé comme modèle et est supprimé si le téléchargement ou la vérification échoue.

## Runtime natif

Le module CMake récupère llama.cpp à une révision Git immuable et compile une bibliothèque JNI `bozer_llama` pour `arm64-v8a` et `x86_64`. Le moteur charge le GGUF depuis le stockage privé, prépare un contexte de 2048 tokens et génère jusqu'à 256 tokens par réponse.

Le modèle choisi est un petit modèle de démarrage, pas un cerveau de niveau ChatGPT. La vitesse et la mémoire consommée dépendront du téléphone. Aucun test réel d'inférence sur le TECNO de l'utilisateur n'a encore été effectué.

## Vérification et limites actuelles

Le workflow GitHub existant `BOZER core tests` valide uniquement les tests Python du noyau BOZER. Il ne compile pas le module Android, CMake/JNI ou l'APK. Il est donc incorrect de présenter le runtime comme déjà validé sur appareil.

La compilation APK reste volontairement différée. Étapes restantes avant de considérer le chat comme opérationnel :
1. compiler le module Android et la bibliothèque native avec le SDK/NDK ;
2. installer sur un téléphone Android et télécharger le modèle ;
3. confirmer le chargement GGUF et une vraie génération en français ;
4. mesurer la RAM, la vitesse et la stabilité sur le téléphone cible.

Ouvrir la racine du dépôt dans Android Studio. SDK cible : Android 35, minimum Android 8.0 (API 26), Java 17.
