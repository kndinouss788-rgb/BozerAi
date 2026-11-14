# BOZER NEURAL V0.1 — état de la base entraînable

## Objectif
Construire un petit modèle de langage propriétaire dont les poids partent d'une initialisation aléatoire. Aucun poids pré-entraîné, modèle téléchargé ou API distante n'est utilisé par ce noyau.

## Architecture
- Transformer causal décodeur, 2 blocs, 4 têtes d'attention.
- Dimension cachée : 64 ; MLP : 256 ; contexte : 128 octets.
- Tokenizer UTF-8 octet par octet : 259 identifiants (PAD/BOS/EOS + 256 octets).
- Embeddings de tokens et de positions appris ; embeddings de sortie liés aux embeddings d'entrée.
- Nombre exact : **124 864 paramètres entraînables**.
- Optimiseur initial : AdamW ; objectif : prédiction du prochain token ; gradient clipping.

## Démarrer
Installer PyTorch : `python -m pip install -r requirements-neural.txt`
Lancer les tests : `python -m unittest discover -s tests -p "test_bozer_neural.py" -v`
Entraîner depuis une initialisation aléatoire : `python -m bozer_neural.train --steps 300`

Le corpus fourni est un minuscule corpus de démonstration original, pas un corpus suffisant pour créer un assistant généraliste. Il sert à valider le pipeline et à apprendre des motifs simples. Pour un modèle utile, il faudra constituer un corpus français de qualité, autorisé, diversifié, dédupliqué et filtré, avec des exemples de dialogue, raisonnement étape par étape, usage d'outils, sécurité et connaissances vérifiables.

## Format de sauvegarde
Le fichier .pt contient le nom de format, la configuration, le state_dict et les métadonnées d'entraînement. Il est généré localement ou comme artefact CI et n'est pas commité dans Git.

## Limites honnêtes
Cette version n'est pas encore reliée à l'application Android. Elle ne possède pas encore de capacités démontrées de raisonnement général, de planification fiable ni de compréhension avancée. Un entraînement sur le corpus de démonstration ne ferait qu'apprendre un petit ensemble de motifs. Le temps d'inférence sur téléphone et la qualité doivent être mesurés plus tard sur l'appareil cible.
