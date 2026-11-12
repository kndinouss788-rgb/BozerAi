# BOZER — étape 2 : fondation du cerveau décisionnel

## Objectif
Séparer la compréhension de la demande, son orientation et la préparation d'action. Le classificateur ne doit jamais exécuter une action, appeler un fournisseur IA ou lancer une recherche de lui-même.

## Premier incrément livré
- `bozer_brain/request_router.py` : classification déterministe en conversation, recherche, action téléphone, mission multi-étapes ou clarification.
- Chaque résultat fournit un niveau de confiance, une explication, des raisons vérifiables et la proposition d'action existante.
- Les demandes de recherche restent inactives tant qu'une couche d'orchestration n'a pas explicitement autorisé la recherche.
- Les demandes multi-actions sont détectées mais ne sont pas exécutées ; elles devront être décomposées et validées par le planificateur.
- Les demandes incomplètes basculent vers une clarification plutôt que d'inventer des paramètres.

## Sécurité et périmètre
Ce module n'importe aucun adaptateur réseau, aucun fournisseur IA et aucun exécuteur Android. Il ne modifie pas le flux de dialogue, les prompts, le modèle local, l'interface ni le routage des fournisseurs. Les permissions Android et confirmations restent obligatoires.

## Tests
Les tests ciblent les cinq routes, les paramètres incomplets et le caractère non-exécutant du classificateur. La réussite de ces tests ne prouve pas encore l'intégration dans l'application Android ni la qualité d'un modèle de langage.

## Prochain incrément
Construire une représentation structurée des missions et un vérificateur de plans, puis connecter ces modules au noyau Python par une API explicite et testée.
