# BOZER — sources de données Mooré

Dernière vérification : 15 novembre 2026.

Ce registre distingue la quantité de données trouvées, leur utilité linguistique et les droits réellement établis. Une ressource téléchargeable ou un dépôt affichant une licence ne suffit pas à prouver que tous les textes tiers qu'il contient sont réutilisables.

## Sources déjà intégrées comme candidates

| Ressource | Volume / contenu | Statut BOZER |
|---|---|---|
| MALM Data — `progressio-media/malm-data` | Lexique de 10 258 enregistrements français–Mooré–anglais; le dépôt déclare MIT. | 10 234 paires uniques sont dans `data/moore_french/malm_candidates.jsonl`. Toutes restent candidates. Le README attribue le contenu à plusieurs ouvrages; la licence des ouvrages sous-jacents n'est pas confirmée indépendamment. |
| Ouily et al. (2024), *A Low-Resource Language Translation: French To Mooré* | Quelques exemples explicites dans un article CC BY 4.0. | Seulement les exemples cités dans le papier ont été intégrés avec attribution et statut candidat. La licence du papier ne s'étend pas automatiquement aux sources qu'il cite. |

## Sources françaises–Mooré repérées mais non importées

| Ressource | Ce qui a été trouvé | Pourquoi BOZER ne l'importe pas encore |
|---|---|---|
| MAFAND-MT / LaFand-MT, configuration Mossi (mos-fr) | 2 287 exemples d'entraînement, 1 478 de validation et 1 574 de test dans la carte du jeu de données. | Le dépôt LaFand-MT annonce CC-BY-4.0-NC pour le corpus; les textes français sources peuvent également avoir des licences différentes selon les sites. Ce corpus n'est donc pas autorisé pour le BOZER commercial sans permissions supplémentaires. Garder les partitions d'évaluation hors entraînement. |
| Kndachraf, `moore-fr-translation` | Environ 6,74k lignes; le viewer montre un mélange de MAFAND-MT et d'entrées du lexique Montivilliers-Nasséré. | La carte ne fournit pas une licence globale suffisamment claire pour tout le mélange. Ne pas copier le dataset agrégé; examiner chaque source séparément. |
| Association Montivilliers-Nasséré, lexique français–mooré | PDF et vocabulaire A–Z en ligne, avec de nombreux mots, expressions et variantes. | Ressource utile pour une éventuelle revue, mais aucune licence explicite de réutilisation commerciale n'a été vérifiée. Demander une autorisation écrite avant extraction substantielle. |
| Mooré Burkina / Webonary | Dictionnaire Mooré–français–anglais téléchargeable, audio et images; le site annonce des milliers de fichiers audio. | Le téléchargement gratuit ne précise pas, à lui seul, les droits de redistribution du texte, des images et des enregistrements. Demander les conditions au détenteur des droits. |

## Source anglaise–Mooré séparée

| Ressource | Ce qui a été trouvé | Décision |
|---|---|---|
| Translatewiki English–Mooré, version OPUS 2026-07-01 | 5 638 paires de phrases anglaises–Mooré, domaine de localisation logicielle; la fiche de distribution annonce CC BY 3.0. | Piste potentielle pour un module EN↔Mooré distinct. L'accès au contenu est restreint dans la fiche consultée; vérifier l'accès, l'attribution et les conditions exactes avant téléchargement et import. Ne pas transformer automatiquement ces paires anglaises en traductions françaises. |

## Règles d'intégration

1. Conserver la source, l'identifiant d'origine, la licence déclarée et le statut de droits pour chaque ligne.
2. Ne jamais traiter « gratuit à télécharger » comme synonyme de domaine public.
3. Ne jamais faire passer une entrée candidate au statut validé par un simple score automatique.
4. Une validation linguistique exige une relecture humaine par un locuteur natif Mooré, avec pseudonyme de relecteur, date, sens, contexte, graphie et notes tonales si nécessaires.
5. Une validation linguistique ne règle pas les droits d'auteur; les deux contrôles sont indépendants.
6. Les corpus non commerciaux ne sont pas utilisés dans un produit BOZER commercial sans autorisation distincte.
7. Les partitions test et validation restent séparées de tout entraînement.
8. Ne pas fabriquer du Mooré par concaténation de mots ni par traduction indirecte non vérifiée.

## Références

- MALM Data : https://github.com/progressio-media/malm-data
- MAFAND-MT / LaFand-MT : https://github.com/masakhane-io/lafand-mt
- MAFAND-MT sur Hugging Face : https://huggingface.co/datasets/masakhane/mafand
- Jeu agrégé Kndachraf : https://huggingface.co/datasets/Kndachraf/moore-fr-translation
- Translatewiki English–Mooré : https://huggingface.co/datasets/burkimbia/translatewiki-eng-mos
- Association Montivilliers-Nasséré : https://www.montivilliersnassere.fr/telechargements/vocabulaire.html
- Lexique PDF Montivilliers-Nasséré : https://www.montivilliersnassere.fr/attachments/francais_moore.pdf
- Mooré Burkina : https://www.mooreburkina.com/
- Dictionnaire Mooré–français–anglais, édition février 2020, compilé par Urs Niggli : https://mooreburkina.com/sites/www.mooreburkina.com/files/pdf-files/Dictionnaire%20Moor%C3%A9%20-%20%20fran%C3%A7ais%20-%20English.pdf
- Ouily et al. (2024) : https://hal.science/hal-04425412v1
