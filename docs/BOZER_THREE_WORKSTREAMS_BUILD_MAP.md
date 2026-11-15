# BOZER — carte complète des éléments à écrire avant intégration

État de référence : 2026-11-15. Cette carte distingue le code de base, les adaptateurs à écrire et les validations futures. Un fichier présent n'est pas une fonction prête pour la production.

## A. BOZER NEURAL — cœur logiciel

### Modules à écrire
- Identité et règles permanentes : profil, politique de confidentialité, refus et limites.
- Entrée conversationnelle : normalisation, contexte, historique court, découpage des tours.
- Routage de langue : français, anglais, Mooré, sélection explicite et détection évaluée.
- Tokenizer et vocabulaire : IDs stables, tokens spéciaux, conservation Unicode/diacritiques.
- Architecture : embeddings, Transformer, attention, MLP, normalisation, sortie logits.
- Génération : décodage, température/top-p, arrêt, streaming, limites de contexte.
- Mémoire : faits, préférences, tâches, provenance, durée de vie, oubli, chiffrement via adaptateur.
- Raisonnement opérationnel : classification d'intention, plan typé, décomposition de tâches.
- Outils : catalogue autorisé, permissions, confirmation, exécution Android, retour de résultat.
- Vérification : distinguer proposition, action exécutée et résultat confirmé.
- Apprentissage : ingestion, provenance, déduplication, filtrage, séparation train/validation/test.
- Évaluation : conversation, Mooré, français, anglais, sécurité, hallucinations, latence, mémoire.
- Export/runtime : checkpoints, quantification, chargement Android, métriques appareil.

### Déjà présents dans le dépôt
Architecture 1B, configuration, entraînement/évaluation de base, tokenizer, données d'entraînement de petite taille, contrat runtime et politique Android. Mémoire/planning/langue viennent d'être ajoutés comme modules de base.
### À compléter avant intégration
Cache KV et génération robuste, persistance mémoire chiffrée, véritable classifieur multilingue, corpus licite et suffisamment grand, pipeline d'entraînement reproductible, checkpoint appris, benchmark indépendant et profilage Android.

## B. BOZER TTS — voix originale masculine

### Modules à écrire
- Profil vocal de référence (adulte, masculin, médium-grave chaleureux, clair).
- Préparation texte : nombres, dates, acronymes, ponctuation, abréviations.
- Prononciation : phonèmes, dictionnaire de prononciation, variantes Mooré et tons.
- Front-end linguistique : segmentation, normalisation, prosodie et pauses.
- Synthèse : adaptateur backend, modèle acoustique, vocodeur et streaming.
- Contrôle : vitesse, volume, interruption, file mono-voix, reprise et erreurs.
- Audio Android : buffers PCM, fréquence, focus audio, sortie haut-parleur/Bluetooth.
- Dataset : consentement écrit, scripts équilibrés, sessions, transcriptions et métadonnées.
- Entraînement : splits par locuteur/session, nettoyage, checkpoints et reproductibilité.
- Évaluation : intelligibilité, naturel, prononciation, bruit, latence, batterie et RAM.
- Licence : contrôle distinct du moteur, du modèle, du dataset et de chaque voix.

### Déjà présents
Profil vocal JSON, validation du corpus, recherche runtime et moteur vocal Android provisoire. Contrat TTS et normalisation de prononciation viennent d'être ajoutés.
### À compléter avant intégration
Backend réellement choisi et épinglé, modèle/poids autorisés, données d'un locuteur consentant, entraînement/finetuning, évaluations et adaptateur Android. Aucun fichier de contrat ne constitue une voix générée.

## C. DICTIONNAIRE ET TRADUCTEUR MOORÉ ↔ FRANÇAIS

### Modules à écrire
- Schéma d'entrée : lemme, sens, catégorie, genre/classe si pertinent, variantes, source, licence.
- Prononciation : graphie exacte, tons, IPA si validée, audio si autorisé.
- Morphologie : formes fléchies, dérivations, préfixes/suffixes documentés.
- Grammaire : pronoms, particules, ordre des mots, négation, temps/aspect.
- Expressions : locutions, proverbes, contexte culturel et équivalence.
- Recherche : index bidirectionnel, accents, suggestions sans altérer la graphie.
- Désambiguïsation : plusieurs sens, contexte, région et niveau de confiance.
- Traduction : mot, expression, phrase; ne pas composer une phrase à partir d'entrées isolées.
- Contribution : provenance, consentement, licence, relecture native, historique de correction.
- Qualité : tests orthographiques, cohérence, doublons, exemples, couverture et jeu de test caché.
- Export : JSONL/SQLite, versions, migrations et compatibilité Android.

### Déjà présents
Schéma cible, lexique, moteur exact et conservateur, audit MALM et file de relecture. Pipeline enveloppe ajouté; il refuse toujours d'inventer les phrases.
### À compléter avant intégration
Validation de locuteurs natifs, résolution des droits des sources candidates, corpus original de phrases, grammaire décrite, morphologie documentée et évaluations de traduction.

## D. Intégration (phase ultérieure seulement)
- Interfaces versionnées et contrats d'erreur.
- Adaptateurs Android pour NEURAL, TTS et dictionnaire.
- Injection de dépendances et gestion de cycle de vie.
- Permissions, confidentialité et stockage local.
- Tests unitaires, intégration, end-to-end, appareils réels.
- Tests de non-régression et correction des défauts observés.

## Ordre de livraison
1. Finir les modules autonomes et leurs tests.
2. Compléter les spécifications et inventaires de données/licences.
3. Faire une revue statique et corriger les modules.
4. Seulement ensuite, intégrer les modules Android.
5. Vérifier sur CI puis sur téléphone réel.

## Critère de fin de la phase « écriture »
Chaque module possède une API claire, des tests unitaires, une documentation d'usage, des limites explicites et aucune dépendance implicite à un module non livré. Les modèles entraînés et les corpus validés sont des livrables séparés du code.
