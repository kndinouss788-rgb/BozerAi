# BOZER AI — Journal d'avancement

Dernière mise à jour : 22 novembre 2026
Branche de construction : `bozer-final-construction`

## Avancement global

**47 % de l'implémentation du produit final.**

Ce pourcentage estime le code et les composants intégrés par rapport au dossier maître complet. Il ne signifie ni que toutes les fonctions sont compilées, ni qu'elles sont validées sur un téléphone, ni qu'un APK final existe. Aucun build ou test automatisé n'a été lancé pendant cette phase de construction.

## Travail intégré dans cette branche

### Cerveau et conversation
- Récupération de la vraie application Android Kotlin/Jetpack Compose conservée dans l'historique Git.
- Cerveau local sélectionné par défaut ; le mode distant ne se déclenche que lorsque l'utilisateur choisit explicitement « En ligne ».
- Modèle Qwen2.5-1.5B-Instruct GGUF Q4_K_M épinglé sur une révision précise, avec taille et SHA-256 attendus.
- Gestionnaire de modèle modifié pour copier le modèle depuis les assets de l'APK vers le stockage privé, vérifier son intégrité et le charger dans llama.cpp. Le téléchargement séparé au premier lancement a été retiré du nouveau chemin.
- Préparation d'un packaging qui télécharge le fichier pendant la construction, vérifie son empreinte et ajoute le binaire et la licence à l'application. Le binaire du modèle n'est pas stocké dans Git.
- Contexte natif augmenté à 2 048 tokens et plafond de génération porté à 128 tokens ; ces valeurs sont des réglages de départ, pas des mesures de performance.
- Réponses déterministes pour les salutations courtes comme « cc » pendant que le moteur local se prépare.
- Ajout de `BozerOrchestrator`, d'un registre des outils et de plans d'étapes vérifiables. Les outils non développés restent marqués comme prévus.

### Mémoire et confidentialité locale
- Historique et souvenirs explicites séparés dans SQLite.
- Consultation, correction, oubli et effacement des souvenirs.
- Export JSON manuel vers le presse-papiers.
- Récupération de souvenirs par pertinence lexicale ; un vrai RAG sémantique reste à construire.
- Journal local des actions ajouté à SQLite. Les numéros de téléphone et corps de SMS sont volontairement exclus du journal.

### Voix
- Reconnaissance vocale Android à la demande avec demande de permission micro.
- TTS Android français avec callbacks de progression par plage de caractères ; le texte prononcé peut être surligné pendant la lecture.
- Aucun mot de réveil permanent ni micro toujours ouvert.

### Actions Android
- Confirmation avant exécution.
- Ouverture d'applications, transfert au composeur et préparation de brouillon SMS sans envoi automatique.
- Alarmes Android exactes via `AlarmManager.setAlarmClock`, avec gestion des permissions exactes et notifications.
- Date relative prévue, notamment « Réveille-moi demain à 6 h 30 ».
- Stockage privé des réveils et restauration après redémarrage.
- Le journal différencie action annulée, permission manquante, ouverture demandée, brouillon préparé et réveil programmé.

### Recherche
- Recherche publique multi-sources : résultats Bing RSS et Wikipédia français en solution de secours.
- Réponses bornées en taille, déduplication, provenance et liens.
- Date affichée seulement quand la source la fournit ; BOZER ne doit jamais fabriquer une date.
- Les résultats externes sont traités comme des données non fiables.
- Le canal Bing RSS est public et non contractuel ; il peut être bloqué ou changé. Son fonctionnement réel n'a pas encore été vérifié dans l'environnement Android cible.

### Interface
- Écran conversation simplifié, orb perle/argent et apparition douce des messages.
- Logo BOZER visible à l'accueil puis masqué dès le début de la conversation.
- Menu supérieur avec historique, Terminal, nouvelle conversation et réglages.
- Terminal inférieur accessible avec « + » et double tapotement dans la zone de saisie.
- Écran dédié de réglages : cerveau/réseau, voix, mémoire, confidentialité, permissions, bulle flottante, stockage, journal des actions et diagnostic.
- Le Terminal présente des intentions ; ses raccourcis ne sont pas vendus comme sept modules complets.

## État des phases du dossier maître

| Phase | État de code actuel | Travail restant majeur |
|---|---|---|
| P0 — Stabiliser le cerveau | En développement — modèle local et chargement intégré préparés | Valider le chemin de génération local après compilation finale ; améliorer stabilité, latence, récupération d'erreurs et diagnostic |
| P1 — Conversation | En développement — contexte, mémoire explicite, historique et « cc » | Réponses longues cohérentes, résumé roulant, tests de régression, recherche sémantique et récupération après interruption |
| P2 — Voix fiable | En développement — reconnaissance Android et TTS avec callbacks de plage | Interruption complète, états visibles, synchronisation effective sur moteurs TTS variés et options vocales |
| P3 — Actions sûres | En développement avancé — apps, composeur, SMS brouillon, alarmes exactes et journal | Gestion/annulation de réveils, cas limites, retours d'exécution et permissions refusées |
| P4 — Recherche et mémoire | En développement — deux sources publiques, liens, dates disponibles, export mémoire | Sources institutionnelles/officielles, robustesse réseau, RAG sémantique, export fichier et suppression vérifiable |
| P5 — Sept modules | Pas encore réalisés comme modules complets | Construire un module à la fois avec outils réels et scénarios d'acceptation |
| P6 — Design final | En développement — surface claire, orb, terminal et réglages | Finition Liquid Glass, animations adaptatives, accessibilité et navigation de l'historique |
| P7 — Pré-lancement | Préparatifs de packaging documentés | Audit sécurité/coûts, signature release, politique de confidentialité, sauvegarde/support et publication |

## Prochaine séquence de construction

1. Consolider le cœur conversationnel local et la continuité multi-tours.
2. Finaliser les états et interruptions vocales.
3. Terminer la sécurité et le journal des actions Android.
4. Renforcer la recherche multi-sources et le RAG mémoire.
5. Construire ensuite les modules spécialisés un par un : BUSINESS, AGENT, RESEARCH, DOCUMENTS, SECRETARY, TRANSLATOR, DEVELOPER.
6. Terminer la finition visuelle et l'écran de diagnostic.
7. Préparer le lancement seulement quand le produit intégré est prêt.

## Limites connues

- Qwen 1.5B quantifié est un cerveau local compact, pas un équivalent de ChatGPT dans le cloud.
- Le modèle sera intégré au paquet final puis copié dans le stockage privé ; prévoir l'espace pour l'APK et sa copie. Le nouveau workflow de packaging n'a pas encore été exécuté.
- La latence, la qualité en français, la RAM, la chauffe et la batterie ne sont pas mesurées.
- La disponibilité réelle du flux Bing RSS n'est pas confirmée ; Wikipédia reste la source de repli.
- Le mode distant et l'erreur historique `invalid_client_id` ne sont pas certifiés corrigés.
- Les sept modules ne sont pas encore construits. Les raccourcis Terminal ne remplacent pas leurs outils.
- Les comportements Android dépendent des permissions, du fabricant et des services installés ; leur statut reste « en développement » tant qu'ils ne sont pas vérifiés.

## Discipline de livraison

Aucun APK ni compilation ne sera lancé pendant cette phase de construction. Les workflows Android et émulateur sont passés en déclenchement manuel uniquement. Ils ne doivent pas être lancés avant que l'intégration de la vision finale soit terminée. Les pourcentages mesurent le travail de code intégré et ne transforment jamais un test non exécuté en résultat positif.
