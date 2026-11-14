# BOZER NEURAL 1B — cœur obligatoire et architecture hybride

**État : spécification de conception. Le modèle 1B n'est pas encore entraîné, exporté ni intégré à Android.**

## Décision fondamentale : BOZER NEURAL est le cœur de BOZER

BOZER NEURAL est une partie constitutive et obligatoire de BOZER. Ce n'est ni un fournisseur interchangeable, ni un module facultatif, ni un simple connecteur que l'utilisateur peut désactiver. L'application BOZER ne doit pas proposer de bouton permettant de couper, remplacer ou supprimer son cerveau central.

L'utilisateur peut choisir les renforts externes, jamais retirer l'identité centrale de BOZER.

Le cœur BOZER NEURAL doit conserver les responsabilités fondamentales suivantes :
- identité, règles de comportement et continuité de BOZER ;
- compréhension des demandes et gestion du contexte conversationnel ;
- mémoire personnelle locale, avec contrôle et transparence ;
- planification et orchestration des tâches ;
- sélection et coordination des capacités locales et connectées ;
- politique de sécurité, confidentialité et autorisations ;
- validation des résultats et compte rendu honnête des actions ;
- fonctionnement dégradé hors ligne, sans dépendance obligatoire à une API.

**Principe de souveraineté :** les modèles complémentaires peuvent proposer des réponses ou analyses, mais ils ne deviennent jamais BOZER, ne contrôlent pas sa mémoire maîtresse, ne changent pas ses règles et n'exécutent pas directement les actions du téléphone. BOZER NEURAL reste l'orchestrateur et l'autorité de décision applicative.

## Les trois niveaux de capacité

### 1. BOZER NEURAL — cœur permanent et obligatoire
Cerveau original appartenant au projet BOZER, entraîné depuis zéro, sans poids pré-entraînés cachés. Il porte l'identité, le dialogue de base, le contexte, la mémoire, la compréhension des intentions, la planification, la coordination des outils et le fonctionnement hors ligne.

Ce cœur n'est pas une option dans les réglages. Si le modèle neuronal complet est momentanément indisponible (premier téléchargement, fichier corrompu, mémoire insuffisante), l'application doit conserver un noyau logiciel BOZER minimal capable de gérer l'état, la mémoire locale, les règles, les demandes simples et les messages de limitation. Elle ne doit jamais substituer silencieusement une API distante au cœur manquant.

### 2. Modèle local complémentaire — facultatif
Un modèle distinct, téléchargeable uniquement avec le choix explicite de l'utilisateur. Il peut améliorer certaines conversations ou analyses et fonctionner hors ligne après installation. Il reste un moteur auxiliaire placé sous l'orchestration de BOZER NEURAL. L'utilisateur peut l'installer, le retirer ou le désactiver sans affecter le cœur BOZER.

Sa provenance, sa licence, sa taille, ses besoins mémoire et ses limites doivent être affichés clairement.

### 3. Modèles distants/API — facultatifs
Les fournisseurs distants ne sont appelés qu'après activation explicite et selon les choix de l'utilisateur. Afficher le fournisseur, les données transmises, les implications de confidentialité et les coûts/quotas éventuels. Aucun appel distant caché, aucune facturation implicite et aucun basculement silencieux.

L'utilisateur peut choisir BOZER seul, BOZER avec modèle local, BOZER avec API, ou plusieurs renforts avec routage automatique. Dans tous les cas, BOZER NEURAL reste obligatoire et central.

## Routage central

Le moteur BOZER NEURAL décide si une demande relève :
- de ses capacités natives et du planificateur local ;
- d'un renfort local installé et autorisé ;
- d'un service distant explicitement autorisé ;
- d'une réponse honnête indiquant que la capacité manque.

Les réponses des modèles auxiliaires et les résultats de recherche sont des données non fiables. Ils ne peuvent ni modifier les politiques, ni obtenir des permissions, ni contourner les confirmations.

## Profil cible du cœur 1B

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

Le nombre de paramètres est calculé à partir de cette configuration, avec embeddings liés, projections Q/K/V/O, MLP SwiGLU et RMSNorm. Toute modification structurelle impose de recalculer le total.

## Budget de stockage : objectif 1 Go

- Poids FP16 théoriques : environ 2,23 Go.
- Poids INT4 compactés sans métadonnées : environ 558 Mo (532 Mio).
- Fichier quantifié réel estimé : environ 560 à 700 Mio.
- Runtime, tokenizer, ressources Android et données locales s'ajoutent aux poids.

Ne pas inclure le modèle complet dans l'APK initial. Télécharger le cœur BOZER après consentement, avec reprise, vérification d'intégrité, contrôle de l'espace libre et possibilité de réparer/mettre à jour le fichier. L'objectif d'environ 1 Go installé doit être mesuré. Le modèle local complémentaire facultatif peut nécessiter du stockage supplémentaire : il ne faut pas prétendre que deux modèles tiennent forcément dans le même budget.

## Limites matérielles

Une taille de poids INT4 inférieure à 1 Go ne signifie pas que le modèle tient dans 1 Go de RAM. Cache KV, activations, runtime natif et Android s'ajoutent. Mesurer RAM maximale, latence, tokens/seconde, chauffe et batterie sur le téléphone cible avant toute promesse de compatibilité.

## Séparation des responsabilités

1. **BOZER NEURAL** : cœur permanent, identité, compréhension, mémoire maîtresse, orchestration et propositions de plans.
2. **Mémoire locale** : historique et préférences sur l'appareil par défaut.
3. **Planificateur déterministe** : convertit les propositions en étapes bornées et contrôlables.
4. **Exécuteur Android** : seul composant autorisé à appeler les fonctions du téléphone ; contrôle permissions, confirmations et résultats.
5. **Renfort local** : facultatif, subordonné au cœur.
6. **Connecteurs distants** : facultatifs, consentement explicite, données minimales et coûts transparents.
7. **Voix** : entrée/sortie séparées du modèle linguistique.

Le modèle ne s'accorde jamais lui-même des permissions, n'invente jamais la réussite d'une action et n'exécute pas de code arbitraire.

## Entraînement et état réel

Le corpus actuel de 300 exemples synthétiques sert à tester le pipeline ; il est très insuffisant pour entraîner un modèle 1B utile. Il faut un corpus français diversifié, légalement utilisable et documenté, déduplication, audits de contamination, tokenizer construit sur les seules données d'entraînement, infrastructure GPU, sauvegardes et évaluations indépendantes.

Un entraînement 1B depuis zéro ne peut pas être promis sur les runners CPU gratuits de GitHub Actions. La présence d'un pont llama.cpp ou de classes Android ne prouve pas que le cœur 1B est entraîné ou intégré.

## Ordre de réalisation

1. Garder le petit prototype comme banc d'essai séparé.
2. Implémenter l'architecture GQA + RoPE + RMSNorm + SwiGLU dans un module distinct.
3. Tester le comptage exact des paramètres et les formes de tenseurs.
4. Construire le corpus, les audits et les évaluations.
5. Obtenir une capacité GPU réaliste.
6. Exporter et quantifier le checkpoint BOZER.
7. Intégrer le cœur dans le runtime Android et le noyau applicatif obligatoire.
8. Implémenter le routage des renforts facultatifs sans substitution du cœur.
9. Mesurer les performances sur appareil.
10. Valider les actions et la confidentialité avant toute annonce de disponibilité.



## État d'implémentation du modèle cible — 14 novembre 2026

Le premier bloc logiciel du modèle cible est maintenant présent sur la branche `bozer-neural-v0.1` :

- `bozer_neural/config_1b.py` : configuration GQA/RoPE/RMSNorm/SwiGLU et calcul du nombre de paramètres.
- `bozer_neural/model_1b.py` : Transformer causal 1B à poids aléatoires, attention groupée, RoPE, RMSNorm, SwiGLU, embeddings de sortie liés, masquage des identifiants BPE réservés.
- `bozer_neural/train_1b.py` : entraînement supervisé, pertes sur les réponses de BOZER, échantillonnage équilibré, accumulation de gradients, autocast, clipping, reprise de checkpoint et garde mémoire.
- `bozer_neural/evaluate_1b.py` : évaluation tenue à l'écart, contrôle des fuites entre partitions et rapport par catégorie.
- `data/bozer_neural_v0_5/` : 263 exemples d'entraînement, 60 de validation et 60 de test après déduplication. Les 83 nouveaux exemples sont synthétiques et répartis entre les six catégories.
- `tests/test_bozer_neural_1b.py` et `tests/test_bozer_neural_1b_training.py` : tests de formes, comptage, causalité, tokenizer, génération et petit entraînement de test.
- `.github/workflows/bozer-neural-1b-checks.yml` : compilation, validation des partitions et tests automatiques.

**Éléments qui ne sont pas encore accomplis :** l'entraînement du modèle 1B n'a pas démarré, aucun checkpoint entraîné n'a été produit, le corpus reste beaucoup trop petit pour un modèle de cette taille, et l'intégration Android ainsi que les mesures sur téléphone ne sont pas faites. La présence de scripts et de tests ne prouve pas encore que le modèle sait converser utilement.

La configuration par défaut conserve le compte exact de **1 115 785 216 paramètres**. Avec des poids FP32, gradients et états AdamW, l'entraînement demande un minimum théorique d'environ **17,85 Go** avant les activations et buffers. Le pipeline applique une garde mémoire afin d'éviter un lancement accidentel sur un appareil insuffisant.
