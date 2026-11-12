# BOZER — étape 2 : fondation du cerveau décisionnel

## Objectif de l'étape

Construire une couche Python portable entre une demande en langage naturel et les adaptateurs Android. Cette couche doit comprendre les demandes prises en charge, préparer des décisions structurées, appliquer les barrières d'autorisation et empêcher BOZER d'annoncer une action comme accomplie sans vérification.

Cette étape ne prétend pas fournir à elle seule un raisonneur général de niveau ChatGPT ni un contrôle universel du téléphone.

## Chaîne de décision livrée

1. **Façade unifiée** — `brain_facade.py` expose `BozerBrain.respond()`. Les conversations sont déléguées au `DialogueEngine` existant ; les demandes d'action passent par le moteur de décision.
2. **Orientation** — `request_router.py` classe les demandes en conversation, recherche, action téléphone, mission multi-étapes ou clarification.
3. **Planification** — `decision_engine.py` décompose les missions simples séparées par « puis », « ensuite », « et enfin » ou un point-virgule. Les étapes sont identifiées et leurs dépendances sont explicites.
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

Les modules existants de dialogue, d'intention, de planification, de mémoire et de registre d'outils sont réutilisés. Le cœur conversationnel, les prompts, les fournisseurs IA, le modèle local et l'interface Android ne sont pas modifiés.

## Sécurité et limites

- Aucun appel Android ni aucune action réelle n'est exécuté par le planificateur.
- Les permissions Android restent contrôlées par le système.
- Les actions sensibles restent soumises à une confirmation explicite.
- Les paramètres manquants ne sont pas inventés.
- Les appels au modèle et à la recherche ne sont pas activés implicitement par la façade.
- Le `DialogueEngine` peut utiliser ses capacités existantes de modèle ou de recherche uniquement lorsque l'appelant les active explicitement.
- Le moteur de décision de cette étape reste déterministe : il ne confie pas encore la décomposition ou l'autorisation des missions à un LLM.
- Les adaptateurs Android réels, l'assistant vocal permanent et le contrôle universel des applications ne sont pas livrés dans cette étape.

## Tests

Commande de référence :

```bash
python -m unittest discover -s tests -v
```

Les tests ciblent le routage, la façade, les missions ordonnées, les permissions, les confirmations, les dépendances et les états de vérification. La validation réelle doit être lue dans les résultats GitHub Actions de la PR ; la présence de tests dans le dépôt ne signifie pas qu'ils ont déjà été exécutés.

Le workflow `.github/workflows/tests.yml` est configuré pour contrôler les PR vers `main` et vers `feature/platform-assistant-foundation`. La branche de fondation de l'étape 1 contient l'ajustement nécessaire pour que la PR de l'étape 2 déclenche ce contrôle.

## Critères de clôture

L'étape 2 peut être déclarée terminée lorsque :
- les tests Python de la PR sont terminés avec succès ;
- le diff et les contrôles automatiques ne signalent aucun défaut bloquant ;
- la PR est prête à être intégrée dans la branche de fondation.

Cette clôture valide la fondation du cerveau décisionnel, pas les fonctions Android des étapes suivantes ni les essais sur téléphone réel.
