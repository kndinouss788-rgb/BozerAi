# BOZER ACTION — moteur de missions

## Périmètre

Ce module Python planifie, stocke et exécute des missions bornées. Il ne constitue pas à lui seul l'intégration Android et ne donne aucun accès implicite au téléphone, au réseau ou aux comptes de l'utilisateur.

Le modèle ne reçoit qu'un catalogue d'outils fourni par l'hôte. Il propose un plan JSON ; seuls les gestionnaires enregistrés par le code de confiance peuvent effectuer des actions.

## Contrôles appliqués

- 32 étapes maximum par mission, 3 tentatives maximum par étape et arguments limités à 64 Kio.
- Identifiants uniques, dépendances présentes et graphe sans cycle.
- Verrou d'exécution SQLite renouvelé périodiquement : deux runners ne peuvent pas exécuter volontairement la même mission en parallèle.
- Permissions et confirmations vérifiées côté exécuteur, pas seulement dans le prompt du modèle.
- Une action n'est terminée que si le gestionnaire renvoie un \`ToolResult\` réussi **et vérifié**.
- Une nouvelle tentative automatique n'est permise que pour un outil explicitement déclaré \`retry_safe=True\`.
- Après interruption, les actions non idempotentes ou nécessitant une confirmation repassent en attente d'approbation. Le système ne les rejoue pas silencieusement.
- Les erreurs du gestionnaire sont capturées sans exposer leur message brut dans le journal.

## Contrat des outils

Chaque gestionnaire reçoit un dictionnaire JSON et doit retourner \`ToolResult\`. Le drapeau \`retry_safe\` ne doit être activé que si répéter exactement l'action ne peut pas produire de doublon ou d'effet secondaire supplémentaire.

Les gestionnaires synchrones doivent imposer leurs propres délais d'attente pour les appels réseau ou matériels. \`max_seconds\` borne la boucle du runner, pas une fonction bloquée à l'intérieur d'un gestionnaire.

## Limites connues

- Le verrou est local au fichier SQLite. Il ne coordonne pas plusieurs appareils qui utiliseraient des bases différentes.
- Une action externe peut avoir été effectuée juste avant un arrêt brutal. Pour éviter toute répétition ambiguë, elle nécessite une approbation humaine à la reprise, sauf si l'outil est explicitement idempotent.
- Le statut "permission absente" bloque la mission ; l'hôte doit fournir la permission puis créer une reprise contrôlée. L'intégration Android des permissions reste un chantier séparé.
- Ce module n'a pas été compilé avec Android et ne valide pas l'exécution réelle sur téléphone.

## Validation

Les tests ciblés sont lancés par \`.github/workflows/agent.yml\` avec \`python -m unittest discover -s tests -p 'test_mission*.py' -v\`. Ce workflow ne lance ni Gradle, ni APK.
