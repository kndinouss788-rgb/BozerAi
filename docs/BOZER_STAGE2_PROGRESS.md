# BOZER — étape 2 : cerveau décisionnel, fondation renforcée

## Ce que cette livraison apporte

Cette PR construit une couche Python entre les demandes en langage naturel et les adaptateurs Android. Elle ajoute une façade unifiée, un routeur déterministe, une préparation de missions ordonnées, un raisonneur de missions optionnel, des contrôles de permissions/confirmations et un vérificateur de résultat.

Cette livraison renforce substantiellement l'étape 2. Elle ne doit pas être présentée comme la clôture intégrale de tous les critères de l'étape 2 du plan directeur tant que les vérifications sur vrais fournisseurs et appareils, la qualité linguistique et les mesures de latence ne sont pas disponibles.

## Chaîne de décision livrée

1. **Façade unifiée** — \`brain_facade.py\` expose \`BozerBrain.respond()\`. Les conversations sont déléguées au \`DialogueEngine\` existant ; les demandes d'action passent par le moteur de décision.
2. **Orientation** — \`request_router.py\` classe les demandes en conversation, recherche, action téléphone, mission multi-étapes ou clarification. Les commandes élémentaires françaises et anglaises couvertes par le parseur ont des tests de routage.
3. **Planification déterministe** — \`decision_engine.py\` décompose les missions simples séparées par « puis », « ensuite », « et enfin », point-virgule, « then » ou « after that ». Les étapes ont des identifiants stables et des dépendances explicites.
4. **Raisonnement de mission optionnel** — \`mission_reasoner.py\` demande au modèle, uniquement avec \`allow_model_reasoning=True\`, une liste JSON stricte de 2 à 6 étapes. Le texte retourné est limité en taille, analysé comme JSON et chaque étape est re-parsée localement. Le modèle ne choisit pas directement un outil et ne peut pas exécuter une action.
5. **Échec prudent** — plusieurs verbes d'action ne sont jamais fusionnés dans une seule cible. Si une étape reste ambiguë, si le JSON est invalide ou si un verbe n'est pas pris en charge, BOZER demande une précision et ne crée aucun plan exploitable.
6. **Autorisation** — \`TaskPlanner\` et \`ToolRegistry\` vérifient les outils connus, les arguments requis, les permissions et les confirmations.
7. **Dépendances** — une étape dépendante reste bloquée si l'étape dont elle dépend n'est pas autorisée.
8. **Routage de fournisseurs à coût contrôlé** — \`provider_router.py\` peut être injecté comme fournisseur de \`ModelGateway\`. Les fournisseurs locaux et payants sont explicitement distingués ; le fallback payant est désactivé par défaut et son nombre de tentatives est plafonné à 2.
9. **Vérification** — \`verification.py\` renvoie \`VERIFIED\`, \`FAILED\` ou \`UNVERIFIED\`. Un simple rapport positif de l'adaptateur ne suffit pas sans état observé concordant.
10. **API publique** — \`bozer_brain/__init__.py\` expose les contrats de décision, de raisonnement et de routage.

## Fichiers concernés

- \`bozer_brain/brain_facade.py\`
- \`bozer_brain/request_router.py\`
- \`bozer_brain/decision_engine.py\`
- \`bozer_brain/mission_reasoner.py\`
- \`bozer_brain/provider_router.py\`
- \`bozer_brain/verification.py\`
- \`bozer_brain/__init__.py\`
- \`tests/test_brain_facade.py\`
- \`tests/test_request_router.py\`
- \`tests/test_decision_engine.py\`
- \`tests/test_mission_reasoner.py\`
- \`tests/test_provider_router.py\`

Les modules existants de dialogue, de contexte, de mémoire, de passerelle de modèle, d'intention, de planification et de registre d'outils sont réutilisés. Le cœur conversationnel Android, les prompts existants, les fournisseurs, le modèle local et l'interface ne sont pas modifiés par cette PR.

## Sécurité et limites de cette livraison

- Aucun appel Android ni aucune action réelle n'est exécuté par le planificateur ou le raisonneur.
- Les permissions Android restent contrôlées par le système.
- Les actions sensibles restent soumises à une confirmation explicite.
- Les paramètres manquants ne sont pas inventés.
- Les appels au modèle et à la recherche ne sont pas activés implicitement par la façade.
- Le \`DialogueEngine\` existant peut utiliser le \`ModelGateway\` et EYE_SEARCH uniquement lorsque l'appelant active explicitement ces capacités.
- Le \`ModelGateway\` existant fournit des adaptateurs Responses, Chat Completions et llama.cpp local, des délais configurables, des limites de taille et une gestion d'erreur assainie.
- Le nouveau \`ProviderRouter\` est une brique injectée, testée avec des faux fournisseurs ; il n'est pas automatiquement câblé par \`DialogueEngine.from_environment\`. Le déploiement doit explicitement construire et injecter la liste des fournisseurs, et autoriser les fournisseurs payants seulement après décision de coût.
- Le nouveau raisonneur de mission est branché à la façade Python via le \`model_gateway\` déjà fourni au DialogueEngine. Son intégration Android et sa qualité auprès d'un vrai fournisseur ne sont pas encore validées.
- Les adaptateurs Android réels, l'assistant vocal permanent et le contrôle universel des applications ne sont pas livrés dans cette étape.

## Validation automatisée

Commande exécutée par GitHub Actions :

\`\`\`bash
python -m unittest discover -s tests -v
\`\`\`

Le premier lot avait 95 tests et a révélé deux échecs de numérotation, corrigés ensuite. Un passage ultérieur a validé 95/95. La suite a ensuite été élargie avec le raisonneur de mission, le routage fournisseur et les cas anglais ; le résultat du passage CI couvrant tous ces changements doit être relu dans le run le plus récent avant de figer le nombre total.

Les tests du raisonneur utilisent un faux fournisseur pour vérifier l'analyse JSON, la gestion des erreurs, les permissions, les confirmations et le rejet des commandes non reconnues. Ils ne constituent pas un appel à un service IA réel.

## État des critères officiels de l'étape 2

Selon \`docs/BOZER_MASTER_PLAN.md\` :

| Critère officiel | État |
|---|---|
| NLU, contexte, planificateur, mémoire de travail et vérificateur | Fondations présentes et tests existants |
| Conversation / recherche / action / mission | Routage français et anglais élémentaire couvert par tests |
| Modèle pour demandes ouvertes | Le DialogueEngine existant peut appeler un modèle avec opt-in ; aucun test de qualité réelle du fournisseur dans cette PR |
| Modèle pour missions complexes | Raisonneur structuré optionnel intégré à la façade Python ; testé avec faux fournisseur, validation live restante |
| Permissions et absence d'action déclenchée par une simple réponse chat | Contrôles locaux conservés et testés |
| Délais, tailles de réponse et erreurs fournisseur | Capacités existantes de ModelGateway testées |
| Fallback multi-fournisseurs et contrôle de coût | Nouveau routeur à coût contrôlé testé ; pas encore injecté automatiquement dans la configuration de production |
| Français et anglais | Cas élémentaires de routage testés ; qualité des réponses non évaluée globalement |
| Mooré et Dioula | Jeu d'essai fiable non disponible dans ce dépôt ; qualité non revendiquée |
| Latence de 2 à 5 secondes | Non mesurée sur le téléphone cible ni sur un fournisseur réel |

## Critères restants avant clôture intégrale

1. Injecter et tester la politique réelle de fournisseurs dans la configuration de déploiement, avec choix local prioritaire et activation payante contrôlée.
2. Évaluer le raisonneur sur un fournisseur réel configuré, sans laisser ses propositions contourner les règles locales.
3. Constituer ou valider des exemples de langue fiables en français, anglais, Mooré et Dioula ; mesurer la qualité au lieu de la supposer.
4. Mesurer la latence sur des échanges simples avec le modèle configuré, notamment sur le téléphone cible ; ne revendiquer la cible 2–5 secondes que si les chiffres l'établissent.
5. Effectuer les essais d'intégration sur Android et sur le téléphone de l'utilisateur lors des étapes adaptées.

La fondation décisionnelle est prête pour revue et intégration. La clôture intégrale de l'étape 2 doit attendre les validations réelles restantes ; les 95 tests verts de l'ancienne révision ne doivent pas être présentés comme la validation de tous les nouveaux modules.
