# BOZER ACTION — pilotage vocal du téléphone

## Objectif

BOZER ACTION transforme une instruction explicite, prononcée ou écrite par l'utilisateur, en une action contrôlée sur le téléphone. Les applications installées deviennent des capacités complémentaires ; les réglages internes restent pilotés directement par BOZER.

Le modèle conversationnel ne reçoit pas un accès autonome au téléphone. Les commandes reconnues sont converties en actions typées et validées par le code Android de confiance. Une demande vague ou non reconnue reste une conversation normale.

## Capacités déjà reliées dans cette branche

### Actions BOZER internes
- Ouvrir les paramètres BOZER.
- Activer ou désactiver le mode silencieux.
- Changer l'ambiance de l'interface (Nacré, Glacier, Sauge).
- Activer ou désactiver les animations et les effets sonores.
- Activer ou désactiver la bulle flottante.
- Changer le moteur de réponse entre Automatique, En ligne et Hors ligne.

### Actions Android directes
- Ouvrir les applications installées visibles par le lanceur Android.
- Ouvrir les pages Android pour Wi-Fi, Bluetooth, son, affichage, localisation, notifications et gestion des applications.
- Préparer un appel dans le composeur, préparer un SMS ou e-mail, partager du texte.
- Programmer des alarmes et rappels BOZER, ouvrir un minuteur et préparer un événement calendrier.
- Ouvrir la caméra système en mode vidéo. L'utilisateur déclenche lui-même l'enregistrement.
- Ouvrir un site Web ou un itinéraire.

### Pilotage vocal d'une application au premier plan
Le service d'accessibilité facultatif, activé manuellement dans les paramètres Android, fournit un premier jeu d'actions génériques :
- Toucher un contrôle visible à partir de son libellé accessible.
- Saisir du texte dans le champ actuellement focalisé (ou dans l'unique champ éditable visible). Les champs marqués comme mot de passe sont ignorés.
- Faire défiler vers le haut ou vers le bas.
- Revenir en arrière ou retourner à l'écran d'accueil.
- Exécuter une séquence de jusqu'à huit étapes explicitement données par l'utilisateur.
- Ouvrir une application précise puis exécuter la séquence dans cette application.

Exemples :
- « Ouvre WhatsApp puis clique sur Messages puis écris Bonjour »
- « Clique sur Paramètres puis fais défiler vers le bas »
- « Ouvre les paramètres Wi-Fi »
- « BOZER, passe en mode silencieux »

Un appui sur la bulle flottante ouvre BOZER directement en mode écoute vocale. Cela permet de lancer une commande depuis une autre application sans rechercher le bouton micro.

## Autorisations et consentement

BOZER ACTION n'obtient pas un droit universel sur tout le téléphone. Android sépare notamment :
- Microphone : demandé lors d'une action vocale.
- Accessibilité : l'utilisateur active manuellement BOZER ACTION dans les réglages Android ; cet accès peut permettre de lire les libellés accessibles et d'interagir avec l'interface visible des applications.
- Affichage par-dessus les autres applications : autorisation spéciale séparée, nécessaire à la bulle.
- Notifications : autorisation système séparée sur les versions Android concernées.
- Contacts, agenda, caméra et autres données protégées : permissions spécifiques à ajouter seulement lorsqu'une capacité en a besoin.

L'écran BOZER ACTION doit expliquer les effets de l'accès avant d'ouvrir les paramètres Android. L'utilisateur garde la possibilité de refuser ou révoquer chaque autorisation.

## Sécurité des actions

- Le service d'accessibilité ne parcourt pas continuellement le contenu des applications. Il examine l'arbre visible uniquement lorsqu'une mission explicitement demandée est en attente.
- Une mission d'interface expire après 45 secondes et ne peut contenir que huit actions.
- Le contrôle de l'interface reste limité à l'application ciblée, ou à la dernière application externe connue pour une action adressée à l'application au premier plan.
- BOZER ignore les champs signalés comme champs de mot de passe.
- Les boutons Envoyer, Publier, Supprimer, Acheter, Payer et Confirmer exigent une confirmation explicite supplémentaire.
- Les appels, SMS, e-mails, partages, événements et changements d'alarmes sensibles passent par une confirmation ou une étape de vérification dans l'application concernée.
- L'action d'accessibilité indique seulement qu'une interaction a été envoyée. L'application cible peut refuser, ignorer ou traiter différemment le clic ; BOZER ne prétend donc pas que l'effet métier est garanti.
- Aucune prise de vue ou vidéo n'est démarrée en arrière-plan. La caméra reste visible et l'utilisateur déclenche l'enregistrement.
- Aucun mot de passe, code ou contenu d'écran n'est envoyé au cerveau distant par ce service.
- Android et certaines applications peuvent bloquer des fenêtres sécurisées, des paiements, des DRM, des écrans d'identification ou des contrôles non exposés à l'accessibilité.

## Limites Android et distribution

L'accessibilité est un accès puissant, sensible et révocable ; ce n'est pas une permission qui transforme BOZER en administrateur du téléphone. Les API Android plus étroites doivent être préférées chaque fois qu'elles permettent d'effectuer une action.

La politique Google Play impose une divulgation visible et le consentement pour les usages de l'Accessibility API qui ne sont pas évidents pour l'utilisateur, exige une description de son usage et interdit notamment le contournement des contrôles de sécurité ou de confidentialité. Elle limite également l'usage de cette API pour les applications qui initient, planifient et exécutent de façon autonome des actions. Pour la distribution sur Play, BOZER ACTION doit donc rester strictement piloté par une instruction explicite de l'utilisateur, sans exécution autonome de missions décidées par le modèle, et son admissibilité doit être vérifiée avant publication.

Une interface tierce peut changer, masquer ses libellés ou ne pas exposer ses boutons. Le contrôle générique n'est donc pas garanti dans chaque application.

## Chantiers suivants

1. Compléter les capacités Android natives : contacts, partage de fichiers, appels avec confirmation, calendrier, gestion des médias et permissions à la demande.
2. Ajouter des adaptateurs dédiés par application lorsque des API ou liens profonds documentés existent.
3. Ajouter le suivi visuel des étapes, les résultats détaillés et la reprise contrôlée des missions.
4. Étendre les verbes d'interface (sélection, maintien, glisser) et la gestion des champs multiples sans deviner quel contrôle l'utilisateur vise.
5. Valider les actions sur plusieurs versions Android et plusieurs fabricants, avec des tests de sécurité pour chaque connecteur.

## Ancien moteur de missions Python

Le module Python planifie, stocke et exécute des missions bornées. Il ne constitue pas à lui seul l'intégration Android et ne donne aucun accès implicite au téléphone, au réseau ou aux comptes de l'utilisateur.

- 32 étapes maximum par mission, 3 tentatives maximum par étape et arguments limités à 64 Kio.
- Identifiants uniques, dépendances présentes et graphe sans cycle.
- Verrou d'exécution SQLite renouvelé périodiquement : deux runners ne peuvent pas exécuter volontairement la même mission en parallèle.
- Permissions et confirmations vérifiées côté exécuteur, pas seulement dans le prompt du modèle.
- Une action n'est terminée que si le gestionnaire renvoie un `ToolResult` réussi et vérifié.
- Une nouvelle tentative automatique n'est permise que pour un outil explicitement déclaré `retry_safe=True`.

## Validation

Des tests unitaires sont ajoutés pour les commandes internes et les actions d'interface génériques. Ils n'ont pas encore été exécutés dans cette session. Aucune compilation Gradle, construction d'APK ni validation sur téléphone n'a été effectuée. Le workflow `.github/workflows/agent.yml` exécute des tests Python ciblés ; il ne valide pas la compilation Android.
