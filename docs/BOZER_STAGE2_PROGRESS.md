# BOZER — étape 2 : cerveau décisionnel, fondation livrée

## Ce que cette livraison apporte

Cette PR construit une fondation Python entre les demandes en langage naturel et les adaptateurs Android. Elle ajoute une façade unifiée, un routeur déterministe, la préparation de missions ordonnées, les contrôles de permissions/confirmations et un vérificateur de résultat.

Elle constitue une livraison de fondation de l'étape 2. Elle ne doit pas être présentée comme la clôture intégrale de tous les critères de l'étape 2 du plan directeur.

## Chaîne de décision livrée

1. **Façade unifiée** — `brain_facade.py` expose `BozerBrain.respond()`. Les conversations sont déléguées au `DialogueEngine` existant ; les demandes d'action passent par le moteur de décision.
2. **Orientation** — `request_router.py` classe les demandes en conversation, recherche, action téléphone, mission multi-étapes ou clarification.
3. **Planification** — `decision_engine.py` décompose les missions simples séparées par « puis », « ensuite », « et enfin » ou un point-virgule. Les étapes ont des identifiants stables et des dépendances explicites.
4. **Échec prudent** — plusieurs verbes d'action ne sont jamais fusionnés dans une seule cible. Si les séparateurs ne sont pas reconnus ou si une étape reste ambiguë, BOZER demande une précision et ne prépare aucun plan exécutable.
5. **Autorisation** — `TaskPlanner` et `ToolRegistry` vérifient les outils connus, les arguments requis, les permissions et les confirmations.
6. **Dépendances** — une étape dépendante reste bloquée si l'étape dont elle dépend n'est pas autorisée.
7. **Vérification** — `verification.py` renvoie `VERIFIED`, `FAILED` ou `UNVERIFIED`. Un simple rapport positif de l'adaptateur ne suffit pas sans état observé concordant.
8. **API publique** — `bozer_brain/__init__.py` expose les contrats de décision et de vérification.

## Fichiers concernés

- `bozer_brain/brain_facade.py`
- `bozer_brain/request_router.py`
- `bozer_brain/decision_engine.py`
- `bozer_brain/verification.py`
- `bozer_brain/__init__.py`
- `tests/test_brain_facade.py`
- `tests/test_request_router.py`
- `tests/test_decision_engine.py`

Les modules existants de dialogue, de contexte, de mémoire, de passerelle de modèle, d'intention, de planification et de registre d'outils sont réutilisés. Le cœur conversationnel Android, les prompts existants, les fournisseurs, le modèle local et l'interface ne sont pas modifiés par cette PR.

## Sécurité et limites de cette livraison

- Aucun appel Android ni aucune action réelle n'est exécuté par le planificateur.
- Les permissions Android restent contrôlées par le système.
- Les actions sensibles restent soumises à une confirmation explicite.
- Les paramètres manquants ne sont pas inventés.
- Les appels au modèle et à la recherche ne sont pas activés implicitement par la façade.
- Le `DialogueEngine` existant peut utiliser le `ModelGateway` et EYE_SEARCH uniquement lorsque l'appelant active explicitement ces capacités.
- Le `ModelGateway` existant fournit des adaptateurs Responses et Chat Completions, des délais configurables, des limites de taille de réponse, une gestion d'erreur assainie et un adaptateur llama.cpp local. Ces capacités sont réutilisées ; cette PR n'ajoute pas de politique de bascule multi-fournisseurs ni un raisonneur LLM de missions.
- Le moteur de décision de cette livraison reste déterministe : il ne confie pas encore la décomposition ou l'autorisation des missions à un LLM.
- Les adaptateurs Android réels, l'assistant vocal permanent et le contrôle universel des applications ne sont pas livrés dans cette étape.

## Validation de cette livraison

Commande exécutée par GitHub Actions :

```bash
python -m unittest discover -s tests -v
```

- Run GitHub Actions : **#308**, terminé avec succès.
- Résultat : **95 tests exécutés, 95 réussis, 0 échec**.
- Le premier passage a révélé un décalage de numérotation des étapes ; la correction a été poussée et validée par le run #308.
- La PR #11 est ouverte et prête pour revue, mais pas fusionnée.

Cette validation couvre le noyau Python. Elle ne prouve ni le bon fonctionnement sur le téléphone de l'utilisateur, ni une intégration du raisonneur LLM dans Android.

## État des critères officiels de l'étape 2

Selon `docs/BOZER_MASTER_PLAN.md`, l'étape 2 exige aussi :

| Critère officiel | État |
|---|---|
| Façade NLU / contexte / planificateur / vérificateur | Fondation présente ; tests Python verts |
| Routage conversation / recherche / action / mission | Présent et testé |
| Réponses honnêtes quand aucun modèle n'est disponible | Couvert par les tests existants du DialogueEngine |
| Aucune action déclenchée par une simple réponse de chat | Couvert par les tests de la façade et du dialogue |
| Raisonnement par modèle pour les demandes ouvertes et missions complexes | Pas encore intégré/validé dans le moteur de décision |
| Politique de routage/fallback multi-fournisseurs | Adaptateurs et sélection de base existants ; fallback multi-fournisseur non validé |
| Qualité linguistique français, anglais, Mooré et Dioula | Pas encore évaluée par un jeu de tests linguistiques |
| Latence de 2 à 5 secondes pour les échanges simples | Non mesurée sur un modèle connecté ni sur un téléphone cible |

## Critères restants avant clôture intégrale de l'étape 2

1. Ajouter et tester le raisonnement structuré par modèle pour les demandes ouvertes, sans lui permettre de contourner le registre d'outils, les permissions ou les confirmations.
2. Valider le routage/fallback des fournisseurs, les délais, les erreurs, les limites de coûts et le comportement sans fournisseur.
3. Établir un jeu d'essai de compréhension/réponse en français puis dans les langues ciblées lorsque des données fiables sont disponibles.
4. Mesurer la latence réelle sur un fournisseur configuré et un appareil cible ; ne revendiquer la cible 2–5 secondes que si les mesures la soutiennent.

La fondation livrée est prête pour revue et intégration. L'étape 2 complète ne doit être déclarée terminée qu'après validation de ces critères restants, conformément au plan directeur.
