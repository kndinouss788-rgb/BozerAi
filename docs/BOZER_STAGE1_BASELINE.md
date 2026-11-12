# BOZER — rapport de référence de l'étape 1

Date d'audit : 12 novembre 2026  
Dépôt : kndinouss788-rgb/BozerAi  
Base main examinée : 5b88612569cff214a5173839f91037578903934b  
Branche de travail : feature/platform-assistant-foundation  
Commit de la sonde et du test examinés : 41e8cf452531acbbebd16f5781f8c0fae8332644  
Pull request : https://github.com/kndinouss788-rgb/BozerAi/pull/10

## 1. Résultat de l'étape

**Étape 1 — fondations, audit et plan directeur : réalisée sur la branche dédiée.**

Les livrables créés :
- plan directeur BOZER avec les 10 étapes, les critères de sortie et l'architecture cible ;
- inventaire de l'état réel du dépôt et des limites Android ;
- sonde Android en lecture seule pour vérifier les capacités de plateforme ;
- test d'instrumentation vérifiant que la sonde ne modifie pas la permission micro et ne prétend pas qu'un service assistant existe ;
- conservation des limites de sécurité, de l'interface approuvée et du cœur conversationnel.

La PR n'est pas déclarée fusionnée. Les contrôles d'émulateur et de déploiement restent suivis séparément.

## 2. Ce qui existe réellement dans le dépôt examiné

### Application Android
- Application Kotlin avec interface Jetpack Compose.
- SDK de compilation/cible 35 et minimum Android 26 ; Java 17.
- Historique et mémoire de faits locaux via SQLite.
- Moteur local llama.cpp appelé depuis Kotlin/JNI ; modèle Qwen2.5-0.5B-Instruct Q4_K_M téléchargé à la demande et vérifié par SHA-256.
- Routage de conversation dans ChatRepository avec modes Automatique, En ligne et Hors ligne ; passerelle distante via RemoteBrainClient et moteur local comme chemin de secours selon configuration/disponibilité.
- Sortie vocale Android TextToSpeech.
- Reconnaissance vocale déclenchée depuis l'interface via RecognizerIntent : ce n'est pas une écoute permanente et ce n'est pas une activation par mot-clé en arrière-plan.
- Actions Android limitées à l'ouverture d'applications, l'ouverture de l'horloge pour préparer un réveil, l'ouverture du composeur téléphonique et la préparation d'un brouillon SMS.

### Limites importantes des actions présentes
- Ouvrir le composeur ne passe pas l'appel.
- Préparer un SMS ouvre l'application de messagerie avec un brouillon ; BOZER ne l'envoie pas directement.
- L'action réveil ouvre l'application Horloge avec l'heure demandée ; l'utilisateur doit terminer/valider dans cette application.
- Les commandes reconnues actuellement sont limitées et ne constituent pas un contrôle universel du téléphone.

### Cerveau et services Python
- Modules de parsing d'intention, dialogue, contexte, mémoire, planification de tâches, registre d'outils, règles de sécurité et EYE_SEARCH.
- Tests de décisions, propositions d'action, limites de contexte, mémoire, passerelle de modèle, recherche et planification.
- Le modèle et la recherche du noyau Python sont soumis à des contrôles explicites d'activation.
- Les clés d'un fournisseur IA doivent rester sur le serveur, jamais dans le code Android ou l'APK.

## 3. Ce qui n'existe pas encore ou n'est pas validé

- Aucun VoiceInteractionService BOZER déclaré dans le manifeste examiné.
- Aucun rôle assistant Android confirmé pour BOZER.
- Aucun moteur de mot-clé « BOZER » intégré/mesuré.
- Aucun accès matériel DSP/SoundTrigger confirmé sur un téléphone cible.
- Aucune permission système invisible ou privilégiée ; BOZER ne peut pas les auto-accorder.
- Aucune automatisation générale et universelle de toutes les applications.
- Aucun moniteur Guardian complet ; seules les capacités disponibles via les API publiques pourront être ajoutées.
- Aucune exploitation complète des missions multi-étapes dans l'application Android.
- Aucun système complet de paiement, abonnement, publication et assistance de production n'est déclaré livré par cet audit.
- Aucun test d'inférence réel actuel sur le téléphone de l'utilisateur n'est établi par ce rapport.

Signalement antérieur à ne pas perdre : une version APK installée précédemment se fermait lorsque l'utilisateur appuyait sur Envoyer. Aucun journal de crash ni reproduction vérifiée n'est joint à cet audit. Ce défaut doit rester un point bloquant de validation sur appareil, et ne doit pas être annoncé comme corrigé sans preuve.

## 4. Sonde de préparation Android ajoutée sur la branche

Fichier : app/src/main/java/ai/bozer/app/BozerPlatformReadiness.kt

La sonde inspecte en lecture seule :
- présence matérielle du microphone ;
- autorisation RECORD_AUDIO ;
- disponibilité du rôle Android assistant ;
- déclaration éventuelle d'un VoiceInteractionService par BOZER ;
- possession effective du rôle assistant ;
- autorisation de notification lorsque la version Android l'exige ;
- exemption d'optimisation batterie.

Elle ne demande aucune permission, n'ouvre aucun réglage, ne démarre pas le micro et n'active aucun mot-clé. Sur le manifeste examiné, aucun VoiceInteractionService BOZER n'est déclaré : la sonde ne doit donc pas présenter l'assistant système comme disponible.

Test ajouté : app/src/androidTest/java/ai/bozer/app/PhoneActionInstrumentedTest.kt — platformReadinessProbeIsReadOnlyAndDoesNotPretendAssistantServiceExists.

## 5. Résultats de vérification CI observés pour le commit 41e8cf4

| Contrôle | Résultat observé | Interprétation |
|---|---|---|
| Tests du noyau Python, run 43250689035 | Réussi — 70 tests | Les tests Python de cette révision passent |
| Compilation APK Android, run 43250688500 | Réussie | L'APK debug a été compilé sur le runner CI |
| E2E Android émulateur, run 43250688793 | En cours au dernier contrôle | Ne pas annoncer un test émulateur réussi tant que le résultat final n'est pas disponible |
| Cloudflare Workers build, build 1ffd97d6-20d8-4677-a07a-2d6358bb0c97 | Échec signalé par le check GitHub | Le résumé public du check ne donne pas la cause détaillée ; diagnostic requis avant de s'appuyer sur ce déploiement |

Ces résultats concernent le commit de la branche de travail, pas nécessairement la version installée sur le téléphone de l'utilisateur.

## 6. Éléments volontairement non modifiés

- Interface approuvée de MainActivity.
- ChatRepository et le flux de conversation.
- RemoteBrainClient et le routage des fournisseurs.
- Prompts et logique de mémoire conversationnelle.
- Intégration llama.cpp et modèle local.
- Aucune demande de permission supplémentaire ou action réelle déclenchée.

## 7. Risques et décisions pour la suite

1. **Crash à l'envoi :** obtenir logcat et reproduire sur l'appareil réel avant de déclarer l'application stable.
2. **Latence locale :** le modèle compact est un point de départ ; mesurer temps de réponse, RAM et chauffe sur appareils modestes.
3. **Écoute de réveil :** concevoir d'abord une vraie intégration VoiceInteractionService ; une écoute logicielle continue peut consommer batterie et reste soumise aux signaux de confidentialité.
4. **Permissions :** l'accord oral BOZER ne remplace pas les dialogues système Android.
5. **Déploiement Worker :** examiner le journal Cloudflare du build en échec avant toute modification de production ; ne pas déduire sa cause.
6. **Régression interface/cerveau :** conserver les règles de gel définies dans le plan directeur.

## 8. Définition de fin de l'étape 1

L'étape 1 est considérée réalisée au niveau fondation parce que le plan, l'inventaire, les limites de sécurité, la sonde et le test associé sont enregistrés dans la branche de travail. Cela ne signifie pas que BOZER est prêt à publier : l'E2E émulateur, le contrôle Cloudflare, la reproduction du crash signalé et les essais réels restent des contrôles à terminer dans les étapes de validation adaptées.
