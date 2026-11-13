# BOZER — interface Android (direction visuelle de référence)

Cette direction devient la référence de l'interface Android pour le cycle actuel. Aucun nouvel écran de tableau de bord ne doit être ajouté sans nécessité produit claire : l'expérience principale reste une conversation unique, légère et immédiatement lisible.

## Intention

BOZER doit être perçu comme une présence calme et intelligente, pas comme un panneau de contrôle. L'utilisateur peut parler, écrire ou démarrer une commande depuis un raccourci. L'interface doit rester compréhensible pour une personne qui découvre BOZER pour la première fois.

## Langage visuel

- Fond perle/blanc froid : `#FFFFFF`, `#F6F8F9`, `#F0F4F5`.
- Texte principal graphite : `#202A2F`.
- Texte secondaire gris minéral : `#7B898F`.
- Traits et surfaces de verre : `#DCE4E7`, blanc à forte opacité.
- Accent argent bleuté discret : `#84969E`.
- Pas de rose dominant, de néon, de dégradé saturé ou de grand panneau sombre.
- L'orbe animé est la présence visuelle de BOZER. Ce n'est pas une nouvelle version du logo officiel.

## Structure de l'écran

1. En-tête compact : réglages à gauche, nom BOZER au centre, lecture vocale à droite.
2. État vide : orbe discret, phrase courte, six raccourcis utiles.
3. Conversation : texte de BOZER libre et lisible, messages utilisateur dans une surface argent très légère.
4. Barre de saisie persistante : raccourcis secondaires, texte, microphone, envoi.
5. Réglages et mémoire dans des dialogues sobres, sans multiplier les écrans.

## Contrat des raccourcis

| Raccourci | Comportement actuel |
| --- | --- |
| Téléphone | Prépare une demande en langage naturel ; le contrôle complet du téléphone reste à construire. |
| Applications | Prépare « Ouvre l'application… » ; l'ouverture existante demande confirmation. |
| Appeler | Prépare « Appelle… » ; Android ouvre le composeur, BOZER ne passe pas l'appel seul. |
| Message | Prépare « Envoie un message à… » ; Android ouvre un brouillon SMS, BOZER ne l'envoie pas seul. |
| Réveil | Prépare « Crée un réveil à… » ; Android ouvre Horloge pour confirmation finale. |
| Recherche | Prépare une recherche Internet reconnue par le moteur actuel (Wikipedia). |
| Mémoire | Accessible dans le menu + et les réglages ; les souvenirs peuvent être modifiés ou oubliés. |
| Voix | Le microphone utilise la reconnaissance vocale Android ; l'icône d'en-tête active/désactive la lecture TTS. |

Les raccourcis préremplissent la saisie : ils ne doivent jamais afficher une réussite avant l'exécution réelle de l'action. Les autorisations et confirmations Android restent obligatoires.

## Éléments à brancher plus tard

Les mêmes emplacements pourront accueillir, lorsque les capacités correspondantes seront réellement disponibles : contacts, navigation, calendrier et rappels, caméra, fichiers, paramètres rapides, automatisations et commandes système. Ajouter un bouton ne signifie pas que sa fonction est déjà prise en charge.

## Logo officiel

Le fichier maître du logo exact fourni par le créateur n'était pas présent dans les ressources Android du dépôt au moment de cette modification. Ne pas le redessiner, le recolorer ou le remplacer par l'orbe. Dès que le fichier maître sera ajouté aux ressources, l'utiliser tel quel pour le logo/wordmark ; conserver l'orbe uniquement comme animation d'état de BOZER.

## Règle de stabilité

Cette interface est considérée comme la référence visuelle pour le cycle actuel. Les prochains travaux doivent prioritairement améliorer le cerveau, la voix, les autorisations et l'exécution réelle des actions, plutôt que relancer une refonte visuelle générale.
