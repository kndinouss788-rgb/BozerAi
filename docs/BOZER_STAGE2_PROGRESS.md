# BOZER — étape 2 : fondation du cerveau décisionnel

## Périmètre livré dans cette étape

Cette étape construit une couche Python portable et testable entre une demande en langage naturel et les adaptateurs Android. Elle prépare des décisions structurées ; elle ne réalise pas les actions à la place d'Android.

### Chaîne de décision

1. **Compréhension déterministe** — réutilise le parseur d'intentions existant pour reconnaître les commandes simples explicitement prises en charge.
2. **Orientation** — `request_router.py` classe la demande en conversation, recherche, action téléphone, mission multi-étapes ou clarification.
3. **Planification** — `decision_engine.py` décompose les missions simples séparées par « puis », « ensuite » ou « et enfin », et crée des dépendances ordonnées.
4. **Autorisation** — `TaskPlanner` et `ToolRegistry` contrôlent les outils connus, les arguments requis, les permissions et les confirmations.
5. **Vérification** — `verification.py` distingue résultat vérifié, échec et résultat non vérifiable. Une réponse positive de l'adaptateur sans état observé ne suffit pas pour annoncer une réussite.
6. **API publique** — `bozer_brain/__init__.py` expose les contrats de décision et de vérification.

## Fichiers ajoutés ou renforcés

- `bozer_brain/request_router.py`
- `bozer_brain/decision_engine.py`
- `bozer_brain/verification.py`
- `tests/test_request_router.py`
- `tests/test_decision_engine.py`
- `bozer_brain/__init__.py`

Les modules existants `intent.py`, `planner.py`, `task_planner.py`, `tools.py`, `memory.py` et `context.py` sont réutilisés, sans remplacement de la mémoire conversationnelle existante.

## Garanties de sécurité

- Aucun appel réseau, recherche web, fournisseur IA ou modèle local n'est lancé depuis cette couche.
- Aucune action Android n'est exécutée par le planificateur.
- Les missions partiellement comprises sont renvoyées en clarification ; les paramètres manquants ne sont pas inventés.
- Les permissions Android et confirmations utilisateur restent obligatoires.
- Le vérificateur n'annonce pas de succès sans état attendu et état observé concordants.

## Limites explicites

Cette étape fournit une **fondation décisionnelle déterministe**, pas encore un raisonnement général de niveau ChatGPT. Le parseur actuel ne comprend qu'un ensemble limité de formulations et de commandes. Les missions multi-étapes ne sont décomposées que lorsqu'elles contiennent des actions reconnues et des séparateurs pris en charge.

La connexion à un modèle de raisonnement, à la recherche web et aux adaptateurs Android réels est laissée aux étapes d'intégration ultérieures. Aucun mot-clé vocal permanent ni contrôle universel de toutes les applications n'est livré ici.

## Tests et validation

Tests ajoutés pour les cinq routes, les demandes incomplètes, la décomposition séquentielle, les blocages d'autorisation et les trois états de vérification.

La commande CI du dépôt est :
```bash
python -m unittest discover -s tests -v
```

Le workflow actuel ne déclenche les tests que pour les pull requests vers `main` et les pushs sur `main`. La PR de cette étape cible la branche de fondation de l'étape 1 ; par conséquent, aucun résultat CI n'est revendiqué pour cette branche tant que le workflow n'est pas déclenché dans une configuration compatible. Les tests écrits ne sont pas présentés comme exécutés.

## Critère de clôture de l'étape 2

La fondation décisionnelle, la planification contrôlée, les barrières d'autorisation, la vérification explicite et leurs tests sont présents dans la branche. L'intégration Android, la connexion au raisonneur IA et la validation sur téléphone restent hors de cette étape et doivent être traitées dans les étapes suivantes.
