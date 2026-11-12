# Multitâche au style iiSU — conception (morceau 3)

Date : 2026-11-12. Fork privé `ligne12/thor-wayfinder`, branche `iisu-look`. Suite du morceau 1 (langage visuel iiSU) et du morceau 2 (barre de volume).

## But

Le balayage vers le haut ouvre l'écran Récents d'Android (launcher3 Quickstep, `com.android.quickstep.RecentsActivity`), une appli système qu'on ne peut pas restyler. Wayfinder le recouvre de son propre multitâche, dessiné dans le langage d'iiSU, avec les fonctions de celui d'AYN.

## Décisions prises (Thomas, 2026-11-12)

- Recouvrir l'écran Récents d'Android plutôt que prendre le bord du bas ou garder celui d'Android : le geste reste celui d'Android.
- Recouvrir dès le début du geste, en fondu, plutôt qu'au lever du doigt.
- Un carrousel de grandes cartes, plutôt qu'une grille.
- Garder les ajouts d'AYN : changer d'écran (« 1/2 »), cadenas, capture d'écran ; et « Tout effacer ».

## Mesures sur le Thor (2026-11-12)

- Navigation par gestes (`navigation_mode` 2). Composant Récents : `com.android.launcher3/com.android.quickstep.RecentsActivity`.
- L'écran Récents démarre dès que le doigt bouge : il est déjà sous l'appli qui rétrécit en carte (vidéo du geste).
- Wayfinder reçoit l'événement de sa fenêtre environ 180 ms après le début du geste, dont 100 ms de `notificationTimeout` dans `accessibility_service_config.xml`. Aujourd'hui il attend encore 350 ms (relecture de la liste des fenêtres) avant d'entrer en mode Récents.
- La liste des tâches se lit sans root (`dumpsys activity recents`). `am stack remove <tâche>` ferme une tâche (essayé).
- Un geste simulé par `input motionevent DOWN/MOVE/UP` avec une pause du doigt ouvre Récents ; `input swipe` file vers l'accueil.
- Le Récents d'AYN montre au-dessus de chaque carte l'écran de l'appli (« 1 ») et un cadenas, et en bas « Capture d'écran » et « Tout effacer ».

## Fonctionnement

1. Le service d'accessibilité reçoit déjà l'événement de fenêtre de Récents (`RecentsRules.isRecents`). À cet événement, il affiche tout de suite le multitâche de Wayfinder sur le même écran : une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` plein écran, qui apparaît en fondu de 150 ms, sans attendre la relecture de 350 ms.
2. L'écran Récents d'Android reste dessous, caché. Le geste « retour » et un nouveau balayage vers le haut agissent sur lui comme avant ; quand il s'en va, le multitâche s'efface aussi en fondu (la détection de sortie existante, `exitRecents`).
3. L'action « Récents » de Wayfinder (double appui sur Retour) ouvre Récents comme aujourd'hui (`GLOBAL_ACTION_RECENTS`) : le multitâche le recouvre de la même façon.
4. Les cartes s'affichent d'abord avec l'icône et le nom de l'appli (dernière liste connue), puis la liste et les aperçus du moment arrivent et remplacent les icônes.
5. Le délai de l'événement (`notificationTimeout`) passe de 100 à 0 ms s'il ne déclenche pas de rafale d'événements sur le Thor ; sinon il reste à 100.

## Sous le capot

Un outil root, sur le modèle de `RecentsTool` (lancé par `PServiceBridge.runEntryPoint`, qui appelle `IActivityTaskManager` par réflexion) :

- **lister** les tâches (`getRecentTasks`) : numéro, appli, écran, ordre ;
- **aperçus** : `getTaskSnapshot(numéro, basse résolution)`, enregistrés en image dans le cache de Wayfinder, lisibles par l'appli ;
- **ouvrir** une tâche : `startActivityFromRecents(numéro)`, comme le fait Quickstep ;
- **fermer** une tâche : `removeTask(numéro)`, comme le fait déjà « Fermer les applis ».

Un lancement de l'outil coûte quelques centaines de millisecondes ; c'est pourquoi les cartes s'affichent d'abord avec la dernière liste connue (point 4).

**Changer d'écran** réutilise le déplacement d'appli existant de Wayfinder. **Le cadenas** est une liste de paquets propre à Wayfinder (préférences, comme les autres réglages) ; « Tout effacer » et l'action « Fermer les applis » de Wayfinder la respectent. **Capture d'écran** enregistre l'aperçu en pleine résolution de la carte choisie dans `Pictures/Screenshots` (MediaStore).

## L'écran

- Le fond à points d'iiSU (`GlassScreen`), le thème, la police et les couleurs suivis comme dans le reste (`ThorGlassTheme`, `IisuLookSource`).
- Un carrousel horizontal de grandes cartes, l'ordre d'Android : les plus anciennes à gauche. Au départ, la carte choisie est celle de l'appli quittée.
- Une carte : l'aperçu dans une case aux coins `Radii.Card` ; au-dessus, l'icône et le nom de l'appli, le badge d'écran (« 1 » ou « 2 ») et le cadenas.
- La carte choisie porte la bande de sélection animée d'iiSU (`focusIndicator`) et grandit de 3 %, comme dans iiSU.
- En bas, la mémoire vive occupée pour donner un ordre d'idée (`RAM 8.0 / 16.0 GB`, relue chaque seconde ; demandé par Thomas pendant la réalisation), puis deux boutons : « Capture d'écran » et « Tout effacer ».
- Les applis des deux écrans sont dans le carrousel ; le badge dit où chacune est. L'écran d'accueil, l'écran Récents lui-même et les tâches exclues des récents par Android n'y sont pas.

## Commandes

Au toucher :

- toucher une carte l'ouvre ;
- la glisser vers le haut la ferme ;
- toucher le badge l'envoie sur l'autre écran ;
- toucher le cadenas la verrouille ou la libère ;
- toucher à côté des cartes, ou Retour, ramène à l'appli d'avant (le retour d'Android sur Récents) ;
- « Tout effacer » ferme toutes les cartes sauf les verrouillées et l'appli affichée sur l'autre écran, puis revient à l'accueil de cet écran.

À la manette, les touches actuelles de Wayfinder dans Récents (`recentsKey`), qui agissent désormais sur le carrousel : gauche/droite pour parcourir, A pour ouvrir, Y ou X pour fermer, Select deux fois pour tout effacer, Start pour l'accueil, B pour revenir. L'aide en bas d'écran (`CueSlots.RECENTS`) reste.

## Cas laissés à Android

- L'outil root ne répond pas, ou la fenêtre ne peut pas être ajoutée : pas de recouvrement, l'écran Récents d'Android reste, avec la manette de Wayfinder comme aujourd'hui.
- Un aperçu illisible : la carte montre l'icône de l'appli seule.
- Un balayage rapide vers l'accueil : le multitâche peut apparaître une fraction de seconde avant de s'effacer.

## Hors périmètre

- L'écran partagé, les fenêtres libres et les paires d'applis de Wayfinder dans le carrousel.
- Reprendre les verrous existants du Récents d'AYN.
- Le centre de notifications (morceau 4).

## Vérification

- Tests unitaires :
  - lecture de la sortie de l'outil root (tâches, écrans, ordre ; ligne illisible ignorée) ;
  - carte choisie au départ (l'appli quittée, sinon la plus récente) ;
  - ce que « Tout effacer » garde (verrouillées, appli de l'autre écran, accueil, Wayfinder).
- Sur le Thor :
  - d'abord, mesurer l'outil root : temps d'un lancement, aperçus lisibles par l'appli ;
  - un vrai geste filmé (`screenrecord`, `input motionevent`) depuis une appli puis depuis l'accueil : le multitâche recouvre l'animation d'Android, et s'efface si le geste finit à l'accueil ;
  - chaque commande au toucher, puis à la manette (les crochets de test de la version de débogage) ;
  - en clair et en sombre ;
  - le geste sur l'écran du bas : s'il ouvre Récents là, le multitâche s'y affiche.

## Risques

- Une mise à jour d'AYN qui changerait Quickstep (nom de l'activité, méthodes cachées de `IActivityTaskManager`) : pas de recouvrement, l'écran d'Android revient, sans casse.
- Le temps de lancement de l'outil root : les aperçus arrivent après l'écran ; s'il dépasse une seconde, garder les aperçus en cache entre deux ouvertures.
- La mémoire des aperçus : basse résolution, et pas plus que les tâches listées.
