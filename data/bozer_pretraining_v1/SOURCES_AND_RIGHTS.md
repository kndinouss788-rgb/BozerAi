# BOZER NEURAL — audit des sources de pré-entraînement français

**Date de recherche : 15 novembre 2026. État : aucun corpus externe importé.**  
Ce document est un inventaire de candidats, pas un avis juridique. Une licence de jeu de données ne règle pas automatiquement les droits de chaque œuvre incorporée, les données personnelles, ni les obligations applicables aux poids entraînés.

## Sources candidates

| Source | Apport possible | Licence/conditions constatées | Décision |
|---|---|---|---|
| [Comptes rendus des débats de l'Assemblée nationale](https://www.data.gouv.fr/datasets/comptes-rendus-des-debats-de-l-assemblee-nationale) | Français oral formel, tours de parole, argumentation et vocabulaire public | La fiche data.gouv indique des comptes rendus intégraux au format XML et une « Licence Ouverte / Open Licence », sans préciser la version sur la fiche consultée. | **Candidat prioritaire**, mais vérifier la version exacte de la licence, les conditions DILA, les éventuels contenus tiers cités et la conservation des dates/intervenants avant ingestion. |
| [Textes des publications de la HAS](https://www.data.gouv.fr/datasets/textes-des-publications-de-la-has-7) | Français technique, administratif et scientifique spécialisé | La fiche indique explicitement la Licence Ouverte 2.0. Elle précise que les textes sont extraits automatiquement des PDF, peuvent être incomplets/erronés et que certaines informations peuvent ne plus être à jour. | **Candidat complémentaire**, après audit qualité et conservation de la date/source. Ne pas en faire une source de vérité médicale. |
| [Wikipédia — dumps et données](https://meta.wikimedia.org/wiki/Research:Data) | Encyclopédie, définitions et prose explicative française | Les conditions Wikimedia prévoient notamment CC BY-SA 4.0 et GFDL pour les contributions textuelles, avec attribution, partage à l'identique selon la licence choisie et exceptions/imports possibles. Les pages/dumps peuvent avoir des conditions d'attribution additionnelles. | **Candidat sous réserve juridique**. Ne pas importer en bloc avant d'avoir audité les pages, les contenus importés, la version du dump et les obligations liées aux poids. |
| [LEGI — textes juridiques](https://www.data.gouv.fr/datasets/legi-codes-lois-et-reglements-consolides-vectorises) | Français juridique structuré | Une fiche dérivée repérée indique LO 2.0 mais contient aussi des vecteurs d'embeddings dont la provenance/modèle n'est pas établie ici. | **Ne pas utiliser les vecteurs**. Si les textes bruts sont retenus, repartir de la source officielle DILA et auditer séparément la licence et les métadonnées. |
| [Common Crawl — conditions d'utilisation](https://www.commoncrawl.org/terms-of-use) | Grand volume de pages web multilingues | Les conditions du service ne constituent pas une licence uniforme des œuvres collectées ; les droits et conditions des sites sources varient. | **Exclu pour l'instant**. Pas d'ingestion massive sans droits vérifiables au niveau des sources. |

## Ce que les licences permettent — et ne permettent pas de conclure

- La [Licence Ouverte 2.0](https://www.data.gouv.fr/pages/legal/licences/etalab-2-0) autorise la réutilisation, l'adaptation et l'exploitation commerciale des informations effectivement couvertes, sous réserve notamment d'attribution de la source et de la date de mise à jour. Elle ne dispense pas de respecter le droit des données personnelles ni les droits de tiers.
- Les conditions Wikimedia permettent certains usages commerciaux sous CC BY-SA/GFDL, mais imposent des obligations d'attribution et, selon la licence choisie, de partage à l'identique. Les contenus importés peuvent avoir des conditions additionnelles.
- « Accessible publiquement », « librement téléchargeable » ou « présent dans un dépôt open source » ne signifie pas « libre pour l'entraînement commercial ».
- La validation juridique de l'usage des textes pour entraîner puis distribuer des poids doit être examinée séparément ; ce fichier ne tranche pas ce point.

## Pipeline BOZER déjà disponible

Le module bozer_neural/prepare_pretraining_corpus.py :
- accepte des fichiers texte locaux UTF-8 ;
- déduplique les paragraphes par SHA-256 normalisé ;
- assigne chaque document source entier à une partition déterministe 90/5/5 ;
- retire certaines formes évidentes d'e-mail, URL et numéros de téléphone ;
- exige un manifeste pour tracer les sources.

Limite importante : le script ne vérifie pas les licences. Le champ allowed_use est une déclaration fournie par l'opérateur ; training_ready n'est pas une certification juridique.

## Taille des données et calcul

Le modèle cible BOZER NEURAL 1B compte 1 115 785 216 paramètres. Le travail de Hoffmann et al. (Chinchilla) montre qu'en régime compute-optimal le nombre de tokens doit croître avec la taille du modèle ; le repère souvent utilisé est environ 20 tokens par paramètre. À titre de planification uniquement, cela donne environ **22,3 milliards de tokens** pour le modèle cible. Ce n'est pas une exigence universelle : le ratio dépend du modèle, de l'objectif, des données et du budget de calcul.

Le corpus conversationnel V0.5 actuellement entraîné ne contient que 22 882 tokens dans sa partition d'entraînement et sert à l'ajustement supervisé, pas au pré-entraînement linguistique général. Il ne remplace pas les milliards de tokens de texte diversifié nécessaires à un objectif 1B.

Le plancher mémoire FP32 poids + gradients + deux moments AdamW est d'environ 17,85 Go, avant activations, buffers et runtime. Le runner CPU gratuit est un banc d'essai pour le petit modèle, pas une infrastructure de pré-entraînement 1B.

## Plan d'ingestion prudent

1. Demander/archiver les conditions exactes pour les débats de l'Assemblée nationale et obtenir confirmation écrite si la licence ou les contenus tiers restent ambigus.
2. Constituer un petit pilote de 100k–1M tokens avec un manifeste complet ; ce pilote sert à tester la qualité et le pipeline, pas à entraîner le 1B.
3. Pour les textes HAS, conserver l'identifiant du document, la date, le type de publication et la version ; filtrer les extractions OCR/XML dégradées.
4. Séparer les sources et documents entre train/validation/test ; ne jamais faire traverser un document source entre partitions.
5. Enregistrer les SHA-256, la licence, l'URL, la date d'acquisition et les règles d'attribution dans le rapport final.
6. Entraîner le tokenizer uniquement sur la partition train et ignorer toute colonne d'embeddings fournie par un dataset tiers.
7. Faire auditer le corpus et les obligations sur les poids avant de lancer un entraînement long ou de distribuer le modèle.

## Références vérifiées

- [Licence Ouverte 2.0 — data.gouv.fr](https://www.data.gouv.fr/pages/legal/licences/etalab-2-0)
- [Débats de l'Assemblée nationale — data.gouv.fr](https://www.data.gouv.fr/datasets/comptes-rendus-des-debats-de-l-assemblee-nationale)
- [Textes des publications HAS — data.gouv.fr](https://www.data.gouv.fr/datasets/textes-des-publications-de-la-has-7)
- [Wikimedia Foundation Terms of Use, section 7](https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use/en)
- [Common Crawl Terms of Use](https://www.commoncrawl.org/terms-of-use)
- [Hoffmann et al., Training Compute-Optimal Large Language Models](https://arxiv.org/abs/2203.15556)
