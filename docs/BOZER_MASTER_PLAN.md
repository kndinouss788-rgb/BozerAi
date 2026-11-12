# BOZER — plan directeur complet

Date de référence : 12 novembre 2026  
Dépôt : https://github.com/kndinouss788-rgb/BozerAi  
Branche de fondation : feature/platform-assistant-foundation

## 1. Vision et promesse produit

BOZER est un assistant personnel vocal intégré au téléphone. Il doit comprendre une intention exprimée naturellement, tenir compte du contexte autorisé, préparer une action, obtenir l'accord requis, l'exécuter par les interfaces Android permises, vérifier le résultat et expliquer honnêtement ce qui s'est passé.

La promesse visée pour la première version publique est : « Je parle à BOZER et il m'aide à faire les choses sur mon téléphone, sans devoir naviguer manuellement dans chaque application pour les tâches qu'il sait prendre en charge. »

Cette promesse ne signifie pas qu'une application Android ordinaire peut prendre le contrôle absolu du téléphone. Les autorisations système, le choix de l'assistant par défaut, les confirmations de sécurité et certaines opérations restent contrôlés par Android. BOZER doit guider l'utilisateur jusqu'aux écrans système nécessaires, jamais les contourner ni prétendre les avoir approuvés.

## 2. Règles non négociables

1. **Vérité des capacités :** aucune fonction n'est annoncée comme opérationnelle tant que son code, ses tests et ses limites sont vérifiés.
2. **Consentement :** accord BOZER pour les actions préparées, accord Android pour les permissions système, confirmation renforcée pour les actions sensibles ou externes.
3. **Vie privée :** écoute de réveil locale autant que possible ; pas d'enregistrement ambiant transmis en continu ; aucune dissimulation de l'indicateur micro Android ; mémoire consultable et effaçable.
4. **Sécurité :** aucune permission système contournée ; aucune action inconnue exécutée ; aucune injection d'instructions venant de résultats web ; journalisation minimale et protégée.
5. **Interface :** conserver l'interface de chat approuvée, claire, blanche/perlée et vitrée. Pas de refonte spontanée ni de tableau de bord encombré.
6. **Cœur conversationnel :** ne pas modifier ChatRepository, RemoteBrainClient, les prompts, le routage des modèles ou la mémoire de conversation sans demande explicite et revue ciblée.
7. **Compatibilité :** viser les téléphones Android accessibles et mesurer RAM, batterie, chaleur et vitesse avant d'élargir les promesses.
8. **Coût maîtrisé :** privilégier les API Android, les composants libres correctement licenciés et les modèles compacts ; protéger les clés côté serveur ; rendre tout coût distant explicite.
9. **Éthique commerciale :** pas de publicité intrusive ; modèle de revenus transparent, sans promesse trompeuse ni mécanisme financier opaque.
10. **Traçabilité :** chaque étape doit laisser un document, un changement de code ciblé, des tests et un statut vérifiable.

## 3. Classement officiel en 10 grandes étapes

### ÉTAPE 1 — Fondations, audit et plan directeur
**But :** établir une base fiable avant d'ajouter des capacités.

Travaux :
- figer la promesse produit, les règles de sécurité, les limites Android et les éléments visuels/conversationnels à protéger ;
- inventorier le dépôt, ses composants réels, ses dépendances et ses fonctions déjà testables ;
- séparer les composants existants, les fondations expérimentales et les fonctions encore à construire ;
- définir l'architecture cible, les contrats entre modules, les critères d'acceptation et les risques ;
- ajouter une sonde de capacités Android en lecture seule et des tests de non-régression ;
- établir la procédure de compilation, de test, de diagnostic et de validation sur appareil.

**Livrables :** ce plan directeur, le rapport de référence de l'étape 1, la sonde Android de préparation à l'assistant et ses tests.

**Sortie de l'étape :** une carte fidèle du projet et un socle reproductible ; aucun faux statut « assistant système » ou « écoute permanente ».

### ÉTAPE 2 — Cerveau BOZER
**But :** faire du cerveau un moteur capable de comprendre et décider, pas un simple ensemble de commandes pré-écrites.

Travaux :
- structurer NLU, contexte, raisonnement, planificateur, mémoire de travail et vérificateur ;
- combiner règles locales rapides pour les intentions sûres et modèle de langage pour les demandes ouvertes ;
- créer un routeur de fournisseurs contrôlé, avec délais, limites de taille, gestion des erreurs et protection contre les coûts inattendus ;
- améliorer les réponses en français puis préparer l'anglais, le Mooré et le Dioula selon les données et tests disponibles ;
- distinguer conversation, recherche, action téléphone et tâche à plusieurs étapes ;
- mesurer la qualité des réponses et le temps de réponse, avec une cible de conception de 2 à 5 secondes pour les échanges simples lorsque le matériel et le réseau le permettent.

**Critères de sortie :** tests de compréhension sur demandes variées, réponses honnêtes sans modèle disponible, aucune action déclenchée par une simple réponse de chat, mesures de latence documentées.

### ÉTAPE 3 — Voix et activation « BOZER »
**But :** permettre une interaction vocale naturelle et fiable.

Travaux :
- séparer session vocale, capture, reconnaissance, détection du réveil et synthèse vocale ;
- commencer par un cycle vocal visible au premier plan : écouter, transcrire, répondre, parler, interrompre et reprendre ;
- étudier puis implémenter le rôle Android VoiceInteractionService avec ses métadonnées et son cycle de session ;
- proposer à l'utilisateur le choix système de BOZER comme assistant, sans prétendre que le rôle est activé avant confirmation ;
- intégrer ensuite une détection locale « BOZER », idéalement par Sound Trigger/DSP quand le téléphone/OEM le permet ;
- mesurer faux réveils, ratés, latence, batterie, RAM, température et qualité selon les accents ;
- offrir activation volontaire, pause/muet et état micro compréhensible.

**Limite obligatoire :** BOZER ne masquera jamais l'indicateur de confidentialité Android. Un service audio ordinaire ne peut pas librement écouter le micro en arrière-plan sur toutes les versions Android.

**Critères de sortie :** fonctionnement documenté par version Android et fabricant ; tests écran éteint/verrouillé/redémarrage/économie d'énergie ; aucune écoute invisible promise.

### ÉTAPE 4 — Contrôle du téléphone
**But :** étendre progressivement les actions réellement exécutables.

Travaux :
- créer un registre de capacités Android : fonction, API/intention utilisée, permission, confirmation et résultat attendu ;
- fiabiliser ouverture d'applications, alarmes, composeur et brouillons SMS ;
- ajouter seulement ensuite contacts, calendrier, notifications, partage, fichiers et réglages lorsque les API publiques et permissions le permettent ;
- afficher une prévisualisation intelligible avant l'action ;
- utiliser les écrans système ou applications cibles lorsqu'Android exige que l'utilisateur termine une opération ;
- ne jamais envoyer un SMS, passer un appel, supprimer, acheter, partager ou modifier un compte sans validation appropriée.

**Critères de sortie :** chaque action testée sur succès, application absente, permission refusée, annulation et erreur système ; résultat rapporté seulement après vérification.

### ÉTAPE 5 — Missions autonomes
**But :** transformer une demande complexe en étapes coordonnées.

Travaux :
- MissionPlanner, ActionQueue, plan de dépendances, sauvegarde d'état et reprise ;
- découpage d'une mission en étapes lisibles et prévisualisées ;
- contrôle des autorisations et confirmations par étape ;
- annulation immédiate et arrêt sûr si une étape échoue ;
- prévention des doubles exécutions et des actions répétées après reprise ;
- vérification du résultat réel de chaque étape ;
- compte rendu final distinguant terminé, partiel, bloqué et annulé.

**Critères de sortie :** missions multi-étapes avec tests d'échec, reprise, interruption, expiration réseau et permission refusée.

### ÉTAPE 6 — BOZER Guardian
**But :** détecter les problèmes du téléphone et aider à les résoudre sans surveillance intrusive.

Travaux :
- surveiller uniquement les métriques publiquement exposées : état de la batterie, espace disponible, connectivité, version de BOZER, erreurs internes et autres signaux permis ;
- examiner la température ou l'état d'autres composants seulement lorsqu'une API et l'appareil les exposent ;
- détecter les problèmes de BOZER lui-même : modèle absent, serveur indisponible, permissions refusées, stockage insuffisant ;
- expliquer la cause probable, le niveau de certitude et les solutions proposées ;
- automatiser uniquement les corrections réversibles et explicitement autorisées.

**Critères de sortie :** métriques sourcées, limites explicites, aucune lecture secrète d'autres applications, aucune promesse de réparer Android intégralement.

### ÉTAPE 7 — BOZER Connect Hub
**But :** connecter BOZER à des services utiles sans couplage fragile.

Travaux :
- créer un registre de connecteurs avec identité, capacités, authentification, permissions, coût, politique de confirmation et vérification du résultat ;
- intégrer recherche web et sources publiques, fichiers, calendrier, navigation et services compatibles ;
- utiliser des API officielles ou des liens profonds documentés ; ne pas contourner les protections de services tiers ;
- isoler les connecteurs défaillants et limiter les données transmises ;
- préparer les connecteurs de paiement/réservation uniquement après étude juridique, sécurité et disponibilité régionale.

**Critères de sortie :** chaque connecteur indique ses prérequis, ses données partagées, son coût éventuel et le statut réel de l'opération.

### ÉTAPE 8 — Mémoire et personnalisation
**But :** rendre BOZER cohérent dans le temps sans collecter plus que nécessaire.

Travaux :
- séparer profil, préférences, mémoire conversationnelle, habitudes et mémoire de mission ;
- demander une autorisation claire avant de retenir une information personnelle durable ;
- fournir consultation, correction, oubli, effacement et export ;
- protéger les données locales et les synchronisations éventuelles ;
- distinguer les éléments locaux des éléments transmis au cerveau distant ;
- éviter les déductions sensibles non demandées.

**Critères de sortie :** tests d'effacement, de restauration, de séparation des comptes et de minimisation des données.

### ÉTAPE 9 — Fiabilité, performance et sécurité
**But :** obtenir une version stable sur de vrais téléphones.

Travaux :
- établir une matrice d'appareils et versions Android, dont téléphones modestes ;
- mesurer temps de démarrage, réponses, RAM, stockage, consommation, chauffe et stabilité ;
- analyser les crashs, notamment le signalement antérieur de fermeture au moment de l'envoi d'un message ;
- tester hors ligne, réseau lent, pertes de connexion, serveur en erreur et modèle absent ;
- réaliser revue sécurité, permissions, réseau, stockage, clés, données personnelles et dépendances/licences ;
- vérifier les parcours vocaux, les missions, les actions et les cas d'annulation ;
- conduire des tests réels avec rapports reproductibles.

**Critères de sortie :** aucun crash bloquant connu non expliqué, tests automatisés verts, mesures sur appareils réels, limitations documentées et procédure de retour arrière.

### ÉTAPE 10 — Première publication et exploitation
**But :** publier uniquement ce que BOZER sait réellement faire.

Travaux :
- définir précisément les fonctions incluses dans la première version et les appareils pris en charge ;
- préparer nom, version, signature, APK, mises à jour, sauvegardes, politique de confidentialité, conditions d'utilisation et assistance ;
- préparer un canal de distribution adapté au budget, puis les magasins officiels lorsque les moyens et conditions le permettent ;
- finaliser le système de paiements réellement disponible au Burkina Faso et à l'international, avec coûts, devises, retraits, remboursements et conformité vérifiés ;
- définir un tarif compréhensible, une période d'essai si retenue, et une séparation nette entre discussion ordinaire et fonctions avancées ;
- établir suivi d'incidents, support, mises à jour de sécurité et publication de notes de version.

**Critères de sortie :** APK signé et reproductible, test d'installation/mise à jour/désinstallation, services disponibles, conditions commerciales claires, aucune fonction présentée comme universelle si elle ne l'est pas.

## 4. Architecture cible à construire progressivement

| Domaine | Modules visés | Responsabilité |
|---|---|---|
| BOZER CORE | NLU, Context, Reasoner, Planner, Verifier, ProviderRouter | Comprendre, décider, répondre et vérifier |
| BOZER VOICE | VoiceSession, SpeechInput, WakeEngine, VoiceOutput | Conversation vocale et activation |
| BOZER ACTION | CapabilityRegistry, PermissionBroker, ApprovalEngine, ActionExecutor | Contrôler les actions du téléphone |
| BOZER MISSION | MissionPlanner, ActionQueue, Scheduler, MissionMemory | Orchestrer les tâches multi-étapes |
| BOZER GUARDIAN | Battery, Storage, Network, AppHealth, Diagnostics | Détecter les problèmes autorisés |
| BOZER MEMORY | Profile, Preferences, Facts, Routines, SecureStorage | Mémoire contrôlée par l'utilisateur |
| BOZER CONNECT | Search, Files, Calendar, Maps, ExternalServices | Connecteurs officiels isolés |
| BOZER PLATFORM | AndroidAssistant, SystemBridge, BootManager, OEMBridge | Intégration Android et future OEM |
| BOZER QUALITY | Tests, Telemetry minimale, Benchmarks, Release | Qualité, sécurité et livraison |

Ce tableau est une architecture cible, pas une déclaration que tous ces modules existent déjà.

## 5. Ordre de priorité avec les moyens actuels

1. Kotlin/Android et API publiques Android en premier.
2. Moteur local rapide pour les tâches courtes et conversation locale lorsque le matériel le permet.
3. Cerveau distant protégé par la passerelle BOZER pour les demandes qui le nécessitent ; pas de clé fournisseur dans l'APK.
4. SQLite et stockage privé pour la mémoire locale.
5. WorkManager seulement pour les tâches différées compatibles avec ses limites, pas pour contourner les restrictions de micro.
6. Tests unitaires Python, tests Kotlin, instrumentation Android, émulateur et vrais téléphones.
7. Modules DSP, permissions privilégiées, préinstallation système et intégration constructeur désactivés jusqu'à preuve d'un support OEM.

## 6. Règles de validation de chaque étape

Une étape n'est terminée que si :
- son périmètre est documenté ;
- les changements sont sur une branche identifiée et relisibles ;
- les tests pertinents ont un résultat vérifié ;
- les limites et fonctions non terminées sont consignées ;
- aucune régression connue n'est dissimulée ;
- l'étape suivante ne dépend pas d'une fonction supposée déjà opérationnelle.

Un build APK réussi prouve la compilation, pas le bon fonctionnement sur le téléphone de l'utilisateur. Un test d'émulateur réussi ne remplace pas un test réel sur les appareils cibles.

## 7. Horizon après la première publication

Après validation du produit Android : partenariat avec un fabricant pour préinstallation et intégration privilégiée ; expérimentation d'un appareil BOZER ; puis étude d'une distribution AOSP/BOZER OS si les ressources d'ingénierie, de maintenance, de sécurité, de certification et de support existent. BOZER Studio et les autres produits de l'écosystème restent des extensions futures et ne doivent pas retarder la fiabilité de l'assistant principal.

## 8. État du plan

- Étape 1 : fondations et audit — réalisée sur la branche de travail ; voir le rapport de référence.
- Étapes 2 à 10 : à engager dans l'ordre, après validation des jalons précédents.
- Le statut des tests CI et des tests réels doit être relu avant toute fusion ou publication.

Documents liés :
- Rapport de référence et vérifications de l'étape 1 : BOZER_STAGE1_BASELINE.md
- Détails d'intégration plateforme Android : BOZER_PLATFORM_ROADMAP.md
