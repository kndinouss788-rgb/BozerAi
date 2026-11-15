# BOZER Mooré–Français — dictionnaire original

## Mission

Construire un dictionnaire Mooré–Français dont BOZER maîtrise la provenance et la qualité des données. Le projet ne recopie pas les entrées d'un dictionnaire tiers sans licence compatible. Les entrées originales peuvent venir de locuteurs contributeurs, d'ateliers de traduction ou de ressources explicitement réutilisables.

## Principes de qualité

- Une entrée candidate n'est jamais présentée comme une traduction certaine.
- Une entrée validée doit être relue par au moins un locuteur natif identifié par un pseudonyme de contributeur (pas de donnée personnelle publique).
- Conserver les graphies et diacritiques Mooré; ne pas supprimer les tons ou signes distinctifs pour simplifier l'index.
- Distinguer les sens, catégories grammaticales, exemples, expressions idiomatiques et variantes régionales.
- Une phrase ne doit pas être assemblée mot à mot si l'ordre ou la grammaire n'a pas été vérifié.
- Les traductions inconnues restent « non trouvées » : BOZER ne doit pas inventer.

## Champs d'une entrée

| Champ | Requis | Description |
|---|---:|---|
| id | oui | Identifiant stable, ex. `mos-noun-000001` |
| moore | oui | Forme Mooré principale, accents/diacritiques conservés |
| french | oui | Sens français correspondant |
| status | oui | `candidate`, `validated` ou `rejected` |
| category | oui | `word`, `phrase`, `expression`, `proverb` ou `sentence` |
| part_of_speech | non | Nom, verbe, adjectif, particule, etc. |
| sense_id | non | Identifiant du sens si le mot est polysémique |
| region | non | Région/variété, si connue et confirmée |
| pronunciation | non | Prononciation ou transcription phonétique |
| tone_notes | non | Notes tonales, sans supposition |
| example_moore | non | Exemple Mooré original |
| example_french | non | Traduction française de l'exemple |
| source_type | oui | `original_contributor`, `open_license`, `fieldwork`, `documentary_reference_pending_rights_review` |
| source_reference | oui | Référence précise ou identifiant de contribution |
| source_license | oui | Licence/conditions de la source |
| reviewed_by | validation | Pseudonyme du relecteur natif |
| reviewed_at | validation | Date ISO de validation |
| notes | non | Ambiguïtés et contexte d'usage |

Les références documentaires dont les droits ne sont pas établis peuvent uniquement alimenter une file de recherche marquée `candidate`. Elles ne doivent pas être présentées comme corpus réutilisable ou validé. Avant toute diffusion commerciale ou réutilisation substantielle, vérifier les droits auprès du détenteur de la source ou remplacer ces propositions par des contributions originales autorisées.\n\n## Workflow de validation

1. Collecter une proposition et sa source.
2. Vérifier la graphie, le sens et le contexte.
3. Faire relire par un locuteur natif Mooré.
4. Faire vérifier le français et l'équivalence du sens.
5. Marquer `validated` seulement après relecture; sinon rester `candidate`.
6. Ajouter des phrases naturelles distinctes des exemples du dictionnaire.
7. Évaluer sur un jeu de phrases réservé, jamais utilisé pour l'entraînement.

## Catégories initiales à couvrir

1. Salutations et formules de politesse
2. Famille et relations humaines
3. Personnes, corps et santé (vocabulaire général, pas de conseil médical)
4. Maison, objets et vêtements
5. Nourriture, cuisine et agriculture
6. Eau, météo, nature et animaux
7. Temps, nombres, dates et quantités
8. Lieux, directions et déplacements
9. École, apprentissage et travail
10. Actions quotidiennes et verbes fréquents
11. Émotions, états et qualités
12. Questions, négation, pronoms et particules grammaticales
13. Expressions idiomatiques et proverbes avec contexte culturel

## Format de données actuel

Le moteur existant lit `data/moore_french/lexicon.json` au format `bozer-moore-french-lexicon-v1`. Les champs détaillés ci-dessus seront ajoutés progressivement de manière rétrocompatible. Ne pas changer la règle actuelle : seules les entrées validées sont proposées par défaut.

## Limite actuelle

Ce document définit la méthode et le format cible. Il ne signifie pas que le dictionnaire est déjà complet ni que les traductions candidates existantes ont été validées. Le corpus doit être construit avec des contributions linguistiques réelles et contrôlées.
