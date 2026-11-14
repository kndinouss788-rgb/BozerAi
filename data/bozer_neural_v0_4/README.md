# BOZER NEURAL corpus V0.4

Corpus français synthétique, créé exclusivement pour entraîner le modèle BOZER depuis des poids aléatoires.

- Entraînement : 180 exemples, 30 par catégorie.
- Validation : 36 exemples, 6 par catégorie.
- Test final : 36 exemples, 6 par catégorie.
- Total : 252 exemples.
- Les 72 nouveaux exemples sont identifiés par `origin=original_synthetic_v0_4` et possèdent un identifiant de scénario.

Les six catégories sont équilibrées : dialogue naturel, compréhension, raisonnement, planification, utilisation/vérification des outils, sécurité/confidentialité/honnêteté.

Les données de validation et de test sont conservées à part et ne servent ni à apprendre le tokenizer ni à mettre à jour les poids. Elles sont reprises de V0.3 ; elles ne sont pas élargies dans cette version.

**Limite :** 180 exemples d'entraînement restent très peu pour un assistant généraliste. C'est un corpus de démarrage destiné à valider l'architecture et la méthode, pas une preuve d'intelligence conversationnelle.
