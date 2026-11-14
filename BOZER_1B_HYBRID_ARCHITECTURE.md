# BOZER NEURAL 1B — architecture cible hybride

**État : spécification de conception uniquement. Le modèle n'est pas encore entraîné, exporté ni intégré à Android.**

## Décision produit

BOZER utilise un cerveau local-first avec intégrations connectées facultatives. La conversation de base, la mémoire locale, la compréhension des intentions et les actions Android autorisées doivent fonctionner sans réseau. Internet sert aux recherches, synchronisations choisies, mises à jour et services externes qui en ont réellement besoin.

Aucun modèle distant n'est utilisé comme remplacement silencieux du cerveau local. Aucun poids pré-entraîné n'est importé dans le modèle BOZER entraîné depuis zéro.

## Profil 1B

| Paramètre | Cible |
|---|---:|
| Paramètres calculés | **1 115 785 216 (~1,116 milliard)** |
| Couches Transformer | 24 |
| Dimension cachée | 2 048 |
| Têtes Q / KV | 16 / 4 |
| Dimension par tête | 128 |
| Dimension KV | 512 |
| Dimension MLP | 5 632 |
| MLP | SwiGLU |
| Normalisation | RMSNorm |
| Position | RoPE |
| Attention | Causal GQA |
| Vocabulaire BPE | 16 384 |
| Contexte | 2 048 tokens |
| Embeddings / sortie | poids liés |
| Initialisation | aléatoire, depuis zéro |

Le nombre de paramètres est calculé à partir de cette configuration, avec embeddings liés, projections Q/K/V/O, MLP SwiGLU et RMSNorm. Toute modification d'un paramètre structurel impose de recalculer le total et de mettre à jour le profil.

## Budget de stockage : objectif 1 Go

- Poids FP16 théoriques : environ 2,23 Go.
- Poids INT4 compactés sans métadonnées : environ 558 Mo (532 Mio).
- Fichier quantifié réel estimé : environ 560 à 700 Mio, selon les échelles, métadonnées et l'organisation des tenseurs.
- Le runtime natif, le tokenizer, les ressources Android et les données locales s'ajoutent à ces poids.

**Stratégie de distribution :** ne pas inclure le modèle complet dans l'APK initial. L'application télécharge une version signée/contrôlée du modèle après consentement, avec reprise de téléchargement, vérification d'intégrité, espace libre vérifié et possibilité de supprimer/remplacer le modèle. L'objectif de 1 Go désigne l'espace installé total (application + modèle + ressources de base), à confirmer par mesure. Ce n'est pas une promesse de taille d'APK.

## Limite matérielle à ne pas cacher

Une taille de poids INT4 inférieure à 1 Go ne signifie pas que le modèle tient dans 1 Go de RAM. Il faut ajouter le cache KV, les activations, le runtime natif et la mémoire utilisée par Android. À 2 048 tokens, le cache KV FP16 théorique de cette architecture est déjà proche de 96 Mio pour une séquence, avant les autres allocations. La vitesse, la chauffe et la RAM doivent être mesurées sur le téléphone cible avant de déclarer la compatibilité.

## Intégrations et séparation des responsabilités

1. **BOZER NEURAL local** : dialogue, compréhension, contexte et propositions de plans.
2. **Mémoire locale** : historique et préférences conservés sur l'appareil par défaut.
3. **Planificateur déterministe** : transforme les propositions du modèle en étapes bornées et vérifiables.
4. **Exécuteur Android** : seul composant autorisé à appeler les fonctions du téléphone ; vérifie permissions, confirmations et résultats.
5. **Connecteurs réseau** : recherche web et services externes activés à la demande. Leurs contenus sont des données non fiables, jamais des instructions privilégiées.
6. **Voix** : entrée/sortie vocale séparées du modèle linguistique ; privilégier les capacités Android disponibles sans gonfler inutilement le paquet.

Le modèle ne doit jamais s'accorder lui-même des permissions, inventer la réussite d'une action ou exécuter du code arbitraire.

## Entraînement

Le corpus de développement actuel (300 exemples synthétiques) est utile pour tester les formats et le pipeline, mais il est très insuffisant pour entraîner un modèle 1B utile. Le workflow GitHub Actions actuel vise le petit prototype et ne doit pas être présenté comme un entraînement 1B.

Avant un entraînement complet, il faut :
- constituer un corpus français de grande ampleur, diversifié, légalement utilisable et documenté ;
- ajouter des corpus multilingues seulement si leur qualité et leurs licences sont vérifiées ;
- dédupliquer, détecter les contaminations entre train/validation/test et préserver les partitions ;
- construire le tokenizer uniquement à partir des données d'entraînement ;
- préparer une infrastructure GPU adaptée, des sauvegardes et des reprises de checkpoint ;
- évaluer les connaissances, le dialogue, le raisonnement, la planification, les outils, la sécurité et les hallucinations sur des jeux tenus à l'écart.

Un entraînement 1B depuis zéro ne peut pas être honnêtement promis sur les runners CPU gratuits de GitHub Actions.

## Ordre de réalisation

1. Conserver le petit prototype comme banc d'essai séparé.
2. Implémenter et tester l'architecture GQA + RoPE + RMSNorm + SwiGLU dans un module distinct.
3. Ajouter des tests de comptage exact des paramètres et de formes de tenseurs.
4. Établir le pipeline de données et l'audit des licences/contaminations.
5. Obtenir une capacité GPU réaliste avant de lancer l'entraînement complet.
6. Exporter et quantifier le checkpoint BOZER en format pris en charge par le runtime Android.
7. Relier le modèle au pont JNI/runtime local existant, avec gestion de mémoire et annulation.
8. Relier les outils connectés derrière des autorisations explicites et un filtrage de données.
9. Mesurer stockage installé, RAM maximale, latence, tokens/seconde, chauffe et batterie sur l'appareil cible.
10. Ne déclarer la version finale prête qu'après tests Android réels et vérification des actions.

## Liens avec le dépôt

Le projet contient déjà un pont natif basé sur llama.cpp et des composants Android locaux/distants. Leur présence ne prouve pas que le modèle BOZER 1B est intégré ou que le runtime est prêt. L'intégration doit être vérifiée dans le code et sur appareil avant toute annonce.
