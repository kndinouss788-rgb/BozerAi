# BOZER AI — Journal d'avancement

Dernière mise à jour : 22 novembre 2026

## Avancement global

**20 % de l'implémentation du produit final.**

Ce pourcentage estime le travail de code intégré par rapport au cahier des charges complet. Il ne représente ni une validation sur téléphone, ni une preuve de qualité du modèle, ni un APK final. Aucun nouveau build ni aucune compilation n'ont été lancés pendant cette phase de construction.

## Travail réalisé dans cette branche

- Récupération d'une base historique complète de l'application Android native Kotlin/Jetpack Compose, au lieu de continuer sur l'exemple générique llama.cpp.
- Conservation de la base SQLite, de l'historique, des souvenirs explicites, du moteur JNI llama.cpp, de la reconnaissance vocale Android, du TTS Android et des premières actions Android confirmées.
- Passage du mode de cerveau par défaut de En ligne à Automatique/local.
- Remplacement du gestionnaire de modèle en téléchargement réseau par un gestionnaire qui copie le fichier GGUF depuis les assets intégrés à l'APK, vérifie sa taille et son SHA-256, puis le conserve dans le stockage privé de l'application.
- Préparation d'un script de packaging qui télécharge le modèle pendant la préparation de l'APK, calcule sa taille et son SHA-256, puis prépare son fichier de licence. Le modèle binaire n'est pas stocké dans Git.
- Ajout d'un workflow de packaging BOZER ciblant la véritable application native.
- Désactivation des déclenchements automatiques des workflows Android build/emulator ; ils sont manuels et ne doivent pas être déclenchés avant la fin de l'implémentation.

## État des phases du dossier maître

| Phase | Contenu | État actuel |
|---|---|---|
| P0 | Fiabilité du cerveau et de la passerelle | En développement — mode local prioritaire intégré ; passerelle distante non certifiée |
| P1 | Conversation, contexte, mémoire et historique | En développement — fondations récupérées ; réponse locale et continuité restent à améliorer |
| P2 | Voix | Fondations récupérées — reconnaissance et TTS Android existent ; synchronisation et robustesse à finir |
| P3 | Actions Android | Fondations récupérées — application, Horloge, composeur, SMS brouillon ; couverture et journal à renforcer |
| P4 | Recherche et mémoire avancées | Partiel — recherche Wikipédia uniquement, mémoire locale explicite |
| P5 | Sept modules spécialisés | À construire un par un |
| P6 | Interface finale | En développement — surface perle existante ; Terminal double-tap et paramètres dédiés à finir |
| P7 | Pré-lancement | À faire — audit, coûts, confidentialité, signature et publication |

## Prochaines étapes de développement

1. Stabiliser le chargement automatique du modèle embarqué et le dialogue local sans compilation prématurée.
2. Terminer l'architecture conversationnelle et le routage intention/réponse/recherche/action.
3. Construire le Terminal inférieur ouvert par double tapotement et l'écran de paramètres dédié.
4. Renforcer la voix et la vérification des actions Android.
5. Ajouter la recherche multi-sources avec sources et dates.
6. Construire les modules spécialisés dans l'ordre, avec leurs outils réels et leurs critères d'acceptation.
7. Finaliser l'interface, la confidentialité, le diagnostic et le packaging final.

## Limites connues

- Le modèle de 1,5 milliard de paramètres n'est pas équivalent à un modèle cloud de pointe. La vitesse et la qualité en français ne sont pas encore mesurées sur les téléphones cibles.
- Le modèle d'environ 1,1 Go sera dans l'APK, puis copié dans les données privées de l'application. L'espace de stockage nécessaire sera donc sensiblement supérieur à la seule taille de l'APK.
- La recherche Android existante est limitée à Wikipédia.
- La voix utilise le fournisseur de reconnaissance Android configuré sur l'appareil ; la préférence hors ligne peut être ignorée par ce fournisseur.
- Le Terminal final, l'écran de réglages dédié et les sept modules ne sont pas encore tous implémentés.
- Les tests sur appareils physiques ne sont pas exécutables depuis l'environnement de développement du dépôt et ne sont pas comptés dans ce pourcentage. Ils restent des validations distinctes avant toute annonce de produit certifié.

## Discipline de livraison

Aucun APK n'est produit pendant la construction. Une compilation ne sera demandée qu'après l'intégration de la vision produit entière. Les validations automatisées pourront être exécutées ensuite dans un environnement contrôlé ; les comportements propres au téléphone resteront explicitement non certifiés tant qu'ils n'auront pas été testés sur des appareils réels.
