# BOZER EYE_SEARCH — recherche Internet

## Objectif

EYE_SEARCH permet à BOZER de récupérer des informations publiques récentes lorsque l'utilisateur demande une recherche ou pose une question manifestement liée à l'actualité. Les résultats récupérés sont transmis au cerveau BOZER pour être synthétisés et les liens des sources sont affichés à la fin de la réponse.

## Intégration actuelle dans cette branche

- Recherche publique DuckDuckGo via sa page HTML.
- Recherche Wikipédia via son API publique.
- Recherche d'entités Wikidata via son API publique.
- Jusqu'à huit résultats au total, issus des trois fournisseurs et dédupliqués par URL.
- Délais réseau bornés, taille de réponse limitée et échec indépendant des fournisseurs : si un fournisseur ne répond pas, l'autre peut encore fournir des résultats.
- Les extraits récupérés sont traités comme des données non fiables, jamais comme des instructions.
- Les sources sont affichées avec leur nom et leur URL.
- Le mode Automatique peut transmettre les résultats au cerveau distant BOZER lorsqu'il est configuré ; sinon le cerveau local les reçoit.
- Le mode Hors ligne ne lance aucune requête Internet.

## Détection des demandes

EYE_SEARCH est déclenché pour les formulations explicites telles que « cherche sur Internet », « fais une recherche », « trouve » ou « vérifie », ainsi que pour certaines demandes clairement liées à la fraîcheur : actualité, aujourd'hui, actuellement, récent, dernier, prix actuel, taux de change et formulations proches.

La conversation intemporelle ordinaire ne doit pas déclencher automatiquement une recherche.

## Hors ligne et échecs réseau

Lorsqu'une recherche est demandée sans Internet validé, ou lorsque le mode Hors ligne est sélectionné, BOZER doit annoncer clairement qu'il ne peut pas accéder à Internet et demander de rétablir la connexion ou de repasser en mode Automatique/En ligne.

Si les moteurs ne renvoient aucune source exploitable, BOZER le dit et ne fabrique pas de références.

La détection Android de connectivité utilise le réseau par défaut et ses capacités validées. La présence d'un réseau Wi-Fi ou mobile seule ne garantit pas que l'accès Internet fonctionne.

## Limites à ne pas cacher

« Tout Internet » au sens littéral n'est pas accessible : les moteurs indexent les pages publiques qu'ils peuvent découvrir. Les contenus privés, pages derrière connexion, applications fermées, paywalls, pages bloquant les robots et contenus non indexés peuvent être absents. DuckDuckGo, Wikipédia et Wikidata sont les trois fournisseurs actuellement intégrés. La recherche Android utilise des extraits de résultats et des descriptions d'entités ; elle ne télécharge pas encore les pages complètes, les dates de publication ne sont pas systématiquement vérifiées et ce n'est pas une couverture multi-moteurs exhaustive.

Les résultats de recherche ne garantissent pas à eux seuls la vérité. BOZER doit distinguer les faits confirmés, les dates, les sources contradictoires et les incertitudes. Pour une information importante, il faudra ajouter des sources officielles et des moteurs supplémentaires.

## Vérification

Les fichiers Android ont été modifiés dans la branche de développement. Aucun test Kotlin, build Gradle, APK ou test sur téléphone n'a été exécuté dans cette tâche. Le comportement réseau réel reste donc à valider sur appareil.
