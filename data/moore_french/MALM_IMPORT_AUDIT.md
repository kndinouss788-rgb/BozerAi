# Import MALM — données du dictionnaire BOZER

- Dépôt source : https://github.com/progressio-media/malm-data
- Fichier source : `lexicon.json`
- Licence publiée par le dépôt : MIT, Copyright (c) 2026 progressio-media.
- Copie de la notice MIT conservée dans `MALM_LICENSE.txt`.
- Provenance d'origine : SRC-17, SRC-11, SRC-13, SRC-07, SRC-04 et SRC-06.
- 10 234 paires françaises–mooré uniques sont intégrées dans le pack de dictionnaire.
- 23 doublons exacts ont été fusionnés; 1 paire déjà présente dans le lexique BOZER a été ignorée.
- Toutes les lignes importées restent `candidate`; elles ne deviennent pas automatiquement validées.
- Le dictionnaire et le traducteur standard peuvent afficher ces résultats avec le statut provisoire et la provenance correspondante.
- Les modes stricts (`include_candidates=False`, `allow_candidates=False`) n'affichent que les entrées relues et validées.
- Les signes diacritiques sont conservés; les formes ambiguës produisent `ambiguous` au lieu d'un choix arbitraire.

Le jeu est exploitable comme dictionnaire de travail sous la notice MIT publiée par son dépôt source. Le statut `candidate` reste distinct d'une validation linguistique par un locuteur natif.
