# BOZER Android — cerveau local pré-entraîné

BOZER sépare l'application Android de son cerveau linguistique local afin de garder l'APK léger.

- **Modèle :** Qwen2.5-0.5B-Instruct (GGUF, quantification Q4_K_M).
- **Licence du modèle :** Apache-2.0.
- **Taille du fichier :** 491 400 032 octets (environ 491 Mo).
- **Exécution :** llama.cpp natif via JNI, directement sur Android.
- **Coût d'inférence locale :** aucun appel API nécessaire pour les conversations exécutées par le modèle local.
- **Distribution :** le GGUF n'est pas embarqué dans l'APK. L'utilisateur choisit de télécharger le cerveau hors ligne depuis les paramètres BOZER.
- **Bulle flottante :** le service fonctionne au premier plan avec une notification persistante et une action d'arrêt ; Android et Google Play imposent une déclaration spécifique pour le type `specialUse`, qui reste à examiner avant publication.
- **Voix française Piper :** le modèle ONNX de 76 733 935 octets est épinglé sur une révision Hugging Face et vérifié par SHA-256 ; le fichier de jetons de 954 octets est épinglé lui aussi. Les téléchargements partiels peuvent reprendre avec HTTP Range et les fichiers vocaux peuvent être supprimés depuis les paramètres pour libérer de l'espace.
- **Persistance :** le modèle est stocké dans le répertoire privé de l'application et n'est pas retéléchargé lors d'une mise à jour normale de l'application.
- **Reprise :** les téléchargements interrompus conservent leur fichier partiel et reprennent via HTTP Range lorsque le serveur le permet.
- **Secrétaire :** une échéance future de tâche crée une alarme locale BOZER ; modifier l'échéance remplace le rappel, et terminer/supprimer la tâche l'annule.
- **Intégrité :** taille attendue et SHA-256 contrôlés avant chargement.
- **Contexte local :** le moteur accepte 1 024 à 2 048 tokens ; le prompt conserve six échanges récents au maximum, quatre faits mémorisés et trois résultats de recherche.

Le cerveau local est facultatif au premier lancement : BOZER ne consomme pas automatiquement environ 491 Mo de données mobiles. Le mode Automatique utilise le cerveau local et n'appelle aucun fournisseur distant en arrière-plan. Le mode En ligne utilise explicitement la passerelle distante ; ses fournisseurs peuvent appliquer des quotas et des frais selon leurs comptes. Pour disposer du mode Automatique/Hors ligne sans réseau, l'utilisateur doit télécharger le modèle une seule fois. Le fichier reste présent après les mises à jour de l'application, mais sera supprimé si l'application est désinstallée ou si ses données sont effacées.

Le modèle constitue la base linguistique pré-entraînée. BOZER ajoute sa propre identité, son ton, sa mémoire locale, ses règles de fiabilité et son système d'actions Android autour de ce modèle. Qwen2.5-0.5B est un petit modèle : il ne doit pas être présenté comme équivalent à ChatGPT. Sa qualité, sa vitesse et sa consommation RAM doivent être mesurées sur les téléphones réels.

## Moteur natif

Le module CMake récupère llama.cpp à une révision Git immuable et compile `bozer_llama` pour `arm64-v8a` et `x86_64`. Le modèle est chargé depuis le stockage privé de l'application.

## État de validation

- Le code de téléchargement à la demande vérifie la taille, le SHA-256 et reprend les transferts interrompus lorsque le serveur accepte les requêtes Range.
- La compilation Gradle ne télécharge plus le GGUF et ne l'intègre plus aux assets de l'APK.
- Le téléchargement du modèle est déclenché par un bouton explicite dans les paramètres BOZER.
- Le chargement d'un modèle déjà installé est prévu au démarrage, sans nouveau téléchargement.
- **Tests Kotlin/Gradle et compilation Android non exécutés dans cette mission à ce stade.**
- **Aucune validation réelle de génération sur un téléphone n'est revendiquée.**

Le chantier expérimental d'entraînement du modèle BOZER NEURAL 1B reste arrêté. Le cerveau Android actif est le modèle pré-entraîné Qwen2.5-0.5B avec l'identité et les règles BOZER.
