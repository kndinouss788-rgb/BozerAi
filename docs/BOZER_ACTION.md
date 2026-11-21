# BOZER ACTION — moteur de missions

## Objectif

BOZER ACTION transforme une intention vocale ou écrite en une action contrôlée. Les applications installées deviennent des capacités externes ; les réglages internes restent pilotés par BOZER. Le modèle propose une intention, mais seul le code Android de confiance décide de ce qui est exécuté.

## Intégration Android présente sur la branche `bozer-neural-v0.1`

- Les commandes vocales reconnues comme actions sont envoyées directement au moteur après la reconnaissance. Une phrase ordinaire reste dans la zone de saisie et n'est pas envoyée automatiquement.
- BOZER peut ouvrir les applications installées dont une activité de lancement est visible par Android, ainsi que des pages Web et des itinéraires.
- Les actions Android déjà intégrées comprennent les alarmes et rappels BOZER, minuteurs, composeur téléphonique, préparation de SMS/e-mails, calendrier et partage. Les envois et appels finaux restent à confirmer dans l'application concernée.
- BOZER peut ouvrir ses propres paramètres et modifier le mode silencieux, le thème visuel (Nacré, Glacier, Sauge), les animations, les effets sonores et la bulle flottante. Les préférences sont persistées dans `bozer_settings`.
- Une demande vidéo générique ouvre l'application caméra en mode vidéo. L'utilisateur appuie lui-même sur Enregistrer.
- « Ouvre TikTok et filme-moi » ouvre TikTok. Si le service d'accessibilité facultatif BOZER ACTION est activé, une tentative limitée de clic sur un bouton visible nommé « Enregistrer » ou « Record » est mise en attente pendant 30 secondes, uniquement dans TikTok. BOZER ne prétend pas que le clic a réussi : l'interface TikTok peut ne pas exposer ce bouton à Android.

## Contrôles de sécurité

- Les applications externes ne reçoivent que des actions explicitement reconnues.
- Les appels, SMS, e-mails, partages, événements et changements d'alarmes sensibles passent par une confirmation ou une étape de vérification dans l'application concernée.
- BOZER ne lit pas les codes, mots de passe ou contenus d'autres applications pour les transmettre.
- Aucune prise de vue ni vidéo n'est démarrée en arrière-plan. La caméra reste visible et l'utilisateur déclenche l'enregistrement.
- Les permissions Android restent sous le contrôle du système et de l'utilisateur.
- Une action n'est pas déclarée réussie si Android refuse son lancement ou si l'application cible est absente.

## Automatisation à l'intérieur des applications

Le service d'accessibilité Android optionnel est déclaré, mais il doit être activé explicitement par l'utilisateur dans les réglages Android. Il ne parcourt pas les écrans en continu : il recherche un contrôle uniquement lorsqu'une demande précise est en attente, uniquement dans les deux paquets TikTok configurés, et uniquement pour le libellé visible « Enregistrer » ou « Record ». La demande expire après 30 secondes. Aucun contenu d'écran n'est copié vers le réseau ou envoyé à BOZER.

Ce pont est un premier connecteur spécialisé, pas encore un automatisme universel pour toutes les applications. Les interfaces tierces changent et peuvent cacher leurs boutons aux services d'accessibilité ; tout clic reste donc à vérifier visuellement par l'utilisateur.

## Ancien moteur de missions Python

Le module Python planifie, stocke et exécute des missions bornées. Il ne constitue pas à lui seul l'intégration Android et ne donne aucun accès implicite au téléphone, au réseau ou aux comptes de l'utilisateur.

- 32 étapes maximum par mission, 3 tentatives maximum par étape et arguments limités à 64 Kio.
- Identifiants uniques, dépendances présentes et graphe sans cycle.
- Verrou d'exécution SQLite renouvelé périodiquement : deux runners ne peuvent pas exécuter volontairement la même mission en parallèle.
- Permissions et confirmations vérifiées côté exécuteur, pas seulement dans le prompt du modèle.
- Une action n'est terminée que si le gestionnaire renvoie un `ToolResult` réussi et vérifié.
- Une nouvelle tentative automatique n'est permise que pour un outil explicitement déclaré `retry_safe=True`.

## Validation et limites

Les tests unitaires Android de reconnaissance des commandes sont ajoutés dans `app/src/test`, mais ils n'ont pas été exécutés dans cette session. Aucune compilation Gradle, construction d'APK ni validation sur téléphone n'a été effectuée. Le workflow `.github/workflows/agent.yml` lance des tests Python ciblés ; il ne valide pas la compilation Android.
