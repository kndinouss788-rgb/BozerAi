# BOZER Android — cerveau local pré-entraîné

BOZER utilise désormais un modèle de langage déjà pré-entraîné comme cerveau de conversation principal :

- **Modèle :** Qwen2.5-0.5B-Instruct (GGUF, quantification Q4_K_M).
- **Licence du modèle :** Apache-2.0.
- **Taille du fichier :** 491 400 032 octets (environ 491 Mo).
- **Exécution :** llama.cpp natif via JNI, directement sur Android.
- **Coût d'inférence :** aucun appel API nécessaire pour les conversations ordinaires.
- **Installation :** téléchargement unique à la demande, puis stockage privé dans l'application.
- **Intégrité :** SHA-256 contrôlé avant chargement.

Le modèle est la base linguistique pré-entraînée. BOZER ajoute sa propre identité, son ton, sa mémoire locale, ses règles de fiabilité et son système d'actions Android autour de ce modèle. La passerelle distante reste une option explicite « En ligne » et n'est plus utilisée par défaut.

## Pourquoi ce modèle

Qwen2.5-0.5B-Instruct est choisi pour son format GGUF, sa licence Apache-2.0, son faible encombrement et sa compatibilité avec le moteur llama.cpp déjà présent dans le projet. Il est adapté à un premier déploiement sur téléphones Android modestes. Ce n'est pas un modèle de niveau ChatGPT : sa qualité, sa vitesse et sa consommation RAM doivent être mesurées sur les appareils réels.

## Moteur natif

Le module CMake récupère llama.cpp à une révision Git immuable et compile `bozer_llama` pour `arm64-v8a` et `x86_64`. Le modèle est chargé depuis le stockage privé de l'application. Le prompt ChatML applique l'identité et les règles BOZER.

## État de validation

- Le code de téléchargement et de vérification SHA-256 existe.
- Le modèle est configuré pour devenir le cerveau local par défaut.
- **Aucune validation réelle de génération sur le TECNO de l'utilisateur n'est revendiquée.**
- La compilation Android/APK et le test sur téléphone restent à effectuer lorsque l'utilisateur demandera explicitement cette étape.

Le chantier expérimental d'entraînement du modèle BOZER NEURAL 1B est arrêté. Son code et ses données sont conservés dans l'historique Git pour archivage, mais ils ne sont plus la voie active du cerveau Android.
