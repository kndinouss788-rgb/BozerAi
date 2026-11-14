# BOZER NEURAL — corpus français V0.2

Corpus initial original, synthétique et rédigé pour BOZER. Les exemples ne sont pas des conversations réelles et ne constituent pas une base de connaissances exhaustive.

## Répartition
- Entraînement : 72 exemples, 12 par catégorie.
- Validation : 12 exemples, 2 par catégorie.
- Test final : 12 exemples, 2 par catégorie.

## Catégories
- dialogue_naturel
- comprehension_demande
- raisonnement_resolution
- planification_missions
- utilisation_outils_verification
- securite_confidentialite_honnetete

## Séparation et précautions
- Chaque ensemble est dans un fichier distinct.
- Les questions des trois ensembles sont différentes.
- Le fichier test ne doit jamais être utilisé pour ajuster les poids ou choisir les paramètres.
- Aucun mot de passe ou donnée personnelle réelle n'est inclus.
- Les exemples de téléphone apprennent à demander l'autorisation et à vérifier l'action avant d'annoncer sa réussite.
- Cette V0.2 est un petit socle de démarrage, pas un corpus suffisant pour un assistant généraliste.
