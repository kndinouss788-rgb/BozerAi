# BOZER Android chat — première intégration

Cette étape ajoute un véritable projet Android Compose avec :
- une interface de conversation en français ;
- un historique SQLite conservé sur l'appareil ;
- l'envoi de l'historique récent au modèle ;
- un client HTTP compatible avec llama.cpp /v1/chat/completions ;
- aucun compte, aucune clé API et aucun service distant requis.

## État réel

Le client Android vise par défaut `http://127.0.0.1:8080/v1/chat/completions`.
Il ne démarre PAS encore llama.cpp et ne télécharge PAS encore le modèle GGUF.
Tant que le runtime local n'est pas intégré et lancé dans le processus Android,
BOZER affiche honnêtement qu'il ne peut pas joindre son cerveau. Aucun faux texte
de réponse n'est généré.

## Projet

Ouvrir la racine du dépôt dans Android Studio. Le module Android est `app`.
SDK cible : Android 35, minimum Android 8.0 (API 26), Java 17.
La compilation APK est volontairement différée jusqu'à la fin du chantier.
