# BOZER NEURAL corpus V0.4

Corpus français synthétique, créé exclusivement pour entraîner le modèle BOZER depuis des poids aléatoires.

- Entraînement : 204 exemples, 34 par catégorie.
- Validation : 48 exemples, 8 par catégorie.
- Test final : 48 exemples, 8 par catégorie.
- Total : 300 exemples.
- Les 72 nouveaux exemples mono-tour et les 48 exemples multi-tours sont identifiés par leur origine et possèdent un identifiant de scénario.

Les six catégories sont équilibrées : dialogue naturel, compréhension, raisonnement, planification, utilisation/vérification des outils, sécurité/confidentialité/honnêteté. Les 48 nouveaux cas multi-tours entraînent le suivi du contexte entre plusieurs échanges.

Les données de validation et de test sont conservées à part et ne servent ni à apprendre le tokenizer ni à mettre à jour les poids. Elles contiennent aussi des dialogues multi-tours originaux.

**Limite :** 204 exemples d'entraînement restent très peu pour un assistant généraliste. C'est un corpus de démarrage destiné à valider l'architecture et la méthode, pas une preuve d'intelligence conversationnelle.
