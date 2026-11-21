# BOZER — dossier de préparation du premier test sur téléphone

**Branche de développement :** `bozer-neural-v0.1`  
**Dernier état documenté :** 21 novembre 2026  
**Statut :** préparation en cours — pas encore une version candidate installable.

## Règle de sortie

BOZER ne doit être remis au propriétaire pour son premier test physique qu'après :
1. revue statique de tous les fichiers modifiés et résolution des incohérences détectées ;
2. exécution des tests Kotlin/Android unitaires et réussite de la compilation native/Gradle ;
3. installation et tests d'intégration automatisés sur émulateur pour le premier démarrage, la base SQLite, le modèle et les permissions ;
4. contrôle de la taille réelle de l'APK, de sa mise à niveau depuis une base existante et des chemins d'erreur ;
5. publication d'un seul APK candidat accompagné de son SHA-256, d'un rapport de tests et de la liste des limites restantes.

Aucun de ces points ne peut être remplacé par un succès de tests Python.

## Intégrations présentes dans la branche

### Conversation et cerveau
- Un moteur llama.cpp natif et un modèle Qwen2.5-0.5B-Instruct GGUF sont prévus.
- Le modèle GGUF d'environ 491 Mo n'est plus un asset embarqué dans l'APK.
- Le téléchargement local est facultatif, explicite, stocké dans le répertoire privé de l'application, vérifié par SHA-256 et reprenable lorsque le serveur accepte HTTP Range.
- Le modèle vérifié est rechargé au démarrage ; une mise à jour normale de l'application ne doit pas le retélécharger.
- AUTO privilégie le modèle local ; ONLINE est une sélection explicite de la passerelle ; OFFLINE est local uniquement.
- Le contexte local, la mémoire explicite et les extraits web sont bornés.

### Voix
- La reconnaissance française en application préfère l'ASR local Android quand il est disponible et demande le mode hors ligne au service système.
- Une transcription vocale normale est automatiquement envoyée comme tour de conversation.
- Les refus de microphone et erreurs communes de reconnaissance sont signalés.
- La synthèse Android suit les segments et, lorsque le moteur le fournit, les plages de caractères. Piper est local et hors ligne ; son alignement visible est au niveau du segment, pas du mot.
- Le modèle Piper MLS est désormais épinglé sur une révision précise ; le fichier ONNX est vérifié par SHA-256, le fichier de jetons par sa taille exacte, et les téléchargements interrompus peuvent reprendre.
- Le profil Piper 55 est un candidat technique et n'a pas été validé par écoute humaine.

### Recherche et actions
- EYE_SEARCH agrège des extraits publics de DuckDuckGo, Wikipédia français et Wikidata ; les sources sont jointes à la réponse.
- Le mode hors ligne annonce l'absence d'accès Internet.
- BOZER ACTION propose le lancement d'applications, réglages, intents Android, et contrôle d'interface explicite via accessibilité avec consentement, limite d'étapes et confirmations sensibles.
- Le contrôle d'interface ne garantit pas la réussite dans toutes les applications tierces.

### Organisation
- Secretary possède une base SQLite, une interface de création, édition, recherche, filtres, priorités, échéances et suppression confirmée. Les commandes vocales/écrites peuvent extraire des échéances françaises simples (demain, jour de semaine, date/mois, heure ou durée relative) et créer le rappel local correspondant ; les dates passées ou invalides sont refusées.
- Les échéances futures peuvent créer une alarme BOZER locale ; modification, complétion et suppression mettent à jour ou annulent le rappel.
- Les alarmes peuvent être créées, listées, modifiées (nom/date/heure/jours), mises en pause/réactivées et supprimées, avec répétitions quotidiennes et jours choisis. Les notifications offrent répétition 10 minutes et arrêt explicite.
- Les alarmes en retard sont traitées lors de la restauration ; les migrations doivent encore être testées sur les anciennes bases.

### Interface et stockage
- L'interface Compose, la surface chat, la bulle flottante et les paramètres existent.
- La bulle utilise un service au premier plan avec notification persistante et action d'arrêt ; le type Android `specialUse` exige une revue de déclaration avant toute publication Play.
- Les paramètres séparent la suppression de l'historique, des faits mémorisés et du cerveau local téléchargé.
- Le cerveau hors ligne peut être supprimé pour libérer environ 491 Mo sans supprimer tâches, alarmes ni conversations.

## Vérifications déjà observées

- Le workflow GitHub **BOZER core tests** s'est terminé en succès sur le commit `6be9496858bd6882d2854bedb1dba928ff936aba`.
- Ce workflow ne comporte qu'un job Python nommé `test`.
- Des tests Kotlin ont été ajoutés ou ajustés pour le calcul de répétition d'alarme, le prompt local et la politique de rappel du Secretary.
- **Ces tests Kotlin n'ont pas été exécutés dans cette étape.**
- **Aucune compilation Gradle/NDK, aucun APK, aucun émulateur et aucun test sur un téléphone réel n'ont été effectués dans cette étape.**

## Bloqueurs avant le premier APK candidat

1. Revue statique des modifications récentes, notamment la logique Compose des alarmes/Secretary, le service spécial de bulle, le téléchargement reprenable du modèle et l'intégration JNI.
2. Exécuter une seule campagne complète : tests Kotlin, compilation native, build APK et intégration émulateur, puis corriger les erreurs avant de produire l'APK candidat.
3. Mesurer la mémoire, la vitesse de chargement, le temps de réponse et la stabilité sur un matériel réel. L'objectif de 2 secondes n'est pas garanti par le modèle 0.5B avant cette mesure.
4. Ajouter une vraie authentification compte/appareil à la passerelle en ligne. `X-Bozer-Client` est un identifiant de quota, pas une authentification ; le mode Online est donc expérimental et ne doit pas être utilisé avec des données sensibles.
5. Ajouter le réveil mains libres « BOZER », le contrôle d'interruption vocale, le flux de réponse progressif et l'alignement mot-à-mot du profil Piper si celui-ci reste le choix vocal.
6. Terminer la récupération web plus profonde : lecture des pages publiques, dates de publication, comparaison des sources, annulation et limites de données.
7. Compléter l'interface de sonnerie/arrêt, les contrôles d'édition/pause des alarmes, puis vérifier Doze, économie d'énergie, démarrage et permissions.
8. Finir les flux d'organisation restants : extraction de dates en français, intégration calendrier, routines, sauvegarde/export.
9. Connecter BOZER ACTION aux missions persistantes Android/Cloudflare avec authentification, suivi, audit, reprise et permissions explicites ; l'agent mission Python/Cloudflare n'est pas actuellement piloté par l'interface Android.
10. Revoir la déclaration Android/Google Play pour le service `specialUse`, les permissions, le stockage, les dépendances, la confidentialité et les mises à jour.

## Hors périmètre du premier test cœur

Le site public, la mini-série, le paiement marchand, l'administration Premium complète, le CRM/stock/factures/PDF et les campagnes sociales sont des chantiers distincts ou partiels. Ils ne doivent pas être présentés comme terminés avec l'application Android cœur.

## Résultat attendu du premier test

Le propriétaire reçoit un seul APK candidat et un rapport qui précise :
- les tests exécutés et leur résultat ;
- le SHA-256 et la taille exacte de l'APK ;
- les permissions à autoriser ;
- le premier téléchargement éventuel du cerveau et la persistance après mise à jour ;
- les limites connues à tester ;
- les erreurs de téléphone à regrouper pour une campagne corrective commune.
