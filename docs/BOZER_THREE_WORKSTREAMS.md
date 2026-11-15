# BOZER — lancement officiel des trois chantiers

Date de lancement : 15 novembre 2026  
Branche de travail : \`bozer-neural-v0.1\`

Les chantiers BOZER IA, BOZER NEURAL et BOZER TTS avancent en parallèle, avec des interfaces contractuelles communes. Le développement reste sur cette branche ; aucune fusion vers \`main\` n'est autorisée par ce document.

## 1. BOZER IA — le corps et l'orchestration

**Mission :** transformer les décisions du noyau BOZER en expériences et actions Android fiables.

Déjà présent dans le dépôt : interface Android, chat, mémoire locale, recherche publique, moteur d'alarmes, bulle flottante, exécution d'intentions via des actions Android autorisées, et confirmations avant les communications sensibles.

Renforcé au lancement :
- Clarification du statut du moteur local Qwen et de la passerelle distante : ce sont des moteurs auxiliaires de génération, pas le modèle original BOZER NEURAL.
- Contrat d'identité/politique commun pour les routes locales et distantes.
- Libellés de l'application corrigés sans refonte de l'écran.
- Contrat vocal Android isolé derrière \`BozerSpeechEngine\`, avec arrêt et libération du moteur.

Prochaines étapes :
1. Stabiliser le build APK et les tests Kotlin.
2. Tester permissions, audio, reprise après interruption, réseau absent et résultats d'actions sur émulateur puis téléphone réel.
3. Renforcer le cycle plan → autorisation → exécution → preuve du résultat → réponse.
4. Brancher progressivement le vrai modèle BOZER NEURAL uniquement après ses évaluations.

## 2. BOZER NEURAL — le noyau cognitif original

**Mission :** construire le modèle et les composants BOZER indépendants, sans présenter un fournisseur ou un modèle tiers comme notre propre cerveau.

État honnête :
- Architecture Transformer depuis zéro configurée à 1 115 785 216 paramètres.
- Architecture, tokenizer et pipeline d'entraînement/évaluation présents.
- Aucun checkpoint 1,116B entraîné et validé n'est actuellement déclaré disponible.
- Le corpus synthétique existant est un jeu de tests logiciel, pas une base suffisante pour préentraîner un modèle d'un milliard de paramètres.

Renforcé au lancement :
- Séparation des données par document source complet, et non par paragraphes indépendants.
- Déduplication globale des blocs.
- Empreintes SHA-256 et rapport de provenance.
- Manifeste de licences/autorisation ; sans métadonnées suffisantes, le corpus est marqué non prêt à l'entraînement.
- Tests de préparation du corpus ajoutés à la CI.

Prochaines étapes :
1. Construire un catalogue de sources françaises et francophones dont les droits d'utilisation sont documentés.
2. Augmenter la qualité et le volume du corpus, en incluant des exemples humains relus et des cas difficiles.
3. Mesurer le coût matériel et valider la disponibilité d'un GPU avant tout entraînement à grande échelle.
4. Comparer sur des jeux de tests tenus à l'écart : dialogue, compréhension, raisonnement, planification, outils, vérification, sécurité.
5. Ne promouvoir aucun checkpoint avant les évaluations hors échantillon et les tests sur appareil.

## 3. BOZER TTS — la voix originale

**Mission :** créer une voix masculine française originale, naturelle et futuriste, inspirée par le niveau de sophistication des assistants de science-fiction sans imiter JARVIS, un acteur ou un personnage.

Identité validée :
- Masculine adulte, registre médium-grave chaleureux, timbre clair et résonant.
- Français naturel, rythme conversationnel, intonation variée et pauses discrètes.
- Calme, intelligent, direct, attentif, humour contenu.
- Voix intelligible sur haut-parleur de téléphone, offline, interruptible, sans dépendance obligatoire à un service payant.

Renforcé au lancement :
- Profil de voix versionné et testé.
- Contrat \`BozerSpeechEngine\` dans l'application Android.
- Adaptateur Android système clairement étiqueté comme provisoire, et non comme la voix finale.
- Validateur de dataset : consentement déclaré, locuteur unique, texte associé, sessions séparées entre train/validation/test, propriétés WAV et chemins sûrs.
- Recherche technique : sherpa-onnx retenu comme candidat de runtime à examiner, pas comme modèle de voix choisi.

Prochaines étapes :
1. Identifier un locuteur adulte consentant et obtenir un accord écrit couvrant l'entraînement et l'usage commercial.
2. Enregistrer un corpus français de qualité, avec sessions séparées.
3. Vérifier séparément licences du runtime, du modèle, des poids et du dataset.
4. Comparer et entraîner une voix BOZER, puis optimiser pour Android.
5. Tester naturel, intelligibilité, latence, consommation, interruptions et stabilité sur téléphone.

## Règles communes

- BOZER NEURAL reste le noyau permanent : identité, mémoire, politique, planification et vérification.
- Les modèles locaux tiers ou fournisseurs distants sont des aides de génération temporaires et explicitement identifiées. Ils ne remplacent pas silencieusement le noyau.
- TTS est une sortie vocale : il ne décide pas des actions et ne transforme pas une proposition en résultat confirmé.
- Android exécute uniquement des actions autorisées et typées ; les permissions et confirmations restent indépendantes des sorties du modèle.
- Aucune clé API, aucun secret et aucune donnée privée ne sont ajoutés aux sources.
- Aucun service payant, GPU payant, hébergement obligatoire ou dette n'est engagé sans décision explicite.
- Pas de promesse de performance sans mesures réelles.

## Vérifications automatiques

La CI BOZER NEURAL a validé sur la branche le 15 novembre 2026 :
- compilation des modules Python ;
- validation des partitions françaises existantes ;
- tests d'architecture 1B et tests d'entraînement de petite taille ;
- tests du préparateur de corpus, du validateur audio TTS et du profil de voix.

Le build Android a révélé une erreur Kotlin dans le prompt de planification (initialiseur déclaré \`const\` alors qu'il utilise \`trimIndent()\`). Le code a été corrigé ; le build APK est en cours de nouvelle vérification. Aucun APK de cette nouvelle série n'est déclaré prêt tant que l'étape de vérification et l'artefact n'ont pas confirmé la réussite.

**Définition de “terminé” :** chaque chantier doit fournir du code versionné, des tests reproductibles, des mesures réelles et un statut honnête. Une fiche d'architecture ou un test de pipeline ne vaut ni modèle entraîné, ni voix créée, ni test sur téléphone.
