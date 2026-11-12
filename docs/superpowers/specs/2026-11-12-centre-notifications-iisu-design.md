# Centre de notifications au style iiSU — conception (morceau 4)

Date : 2026-11-12. Fork privé `ligne12/thor-wayfinder`, branche `iisu-look`. Suite des morceaux 1 (langage visuel), 2 (barre de volume) et 3 (multitâche). Le morceau 5, le centre de contrôle, sera refait juste après avec les briques communes posées ici.

## But

Le balayage du haut vers le bas ouvre aujourd'hui le volet d'Android (SystemUI) : les tuiles d'AYN puis les notifications. Wayfinder le remplace par son propre centre de notifications, dessiné comme le panneau de notifications d'iiSU (accueil d'iiSU, cloche, onglet « System Notifications »), avec en plus une rangée de réglages rapides.

## Décisions prises (Thomas, 2026-11-12)

- Contenu : les notifications et des tuiles rapides.
- Forme : le volet droit d'iiSU, le jeu reste visible à gauche.
- Tuiles : les interrupteurs Android que Wayfinder sait déjà piloter ; les actions de jeu restent au centre de contrôle (bouton AYN).
- Actions : comme iiSU (ouvrir, effacer, tout effacer) et les boutons d'action des applis ; pas de réponse écrite.
- Approche : bloquer le volet d'Android et capter le geste soi-même, plutôt que le recouvrir.
- Le centre de contrôle suivra ce même langage (morceau 5), pour que tous les réglages se ressemblent.

## Mesures sur le Thor (2026-11-12)

- Le volet d'Android est la fenêtre `NotificationShade` (type 2040), sur l'écran du haut seulement.
- `cmd statusbar send-disable-flag statusbar-expansion` empêche de le déployer ; `cmd statusbar send-disable-flag none` le rétablit. La commande passe par le root d'AYN (`PServiceBridge.exec`).
- iiSU lit déjà les notifications (`com.iisulauncher/.notifications.SystemNotificationListenerService`). Son panneau : un volet à droite d'environ 60 % de la largeur, un titre « System Notifications » et un bouton « Clear notifications » en pastille ; une carte blanche par notification, avec l'icône ronde de l'appli, son nom en gras et l'heure relative (« Il y a 3 minutes »), le titre en gras, puis le texte.
- Tuiles du volet d'AYN (`sysui_qs_tiles`) : internet, performancemode, fan, ledlights, controllerStyle, triggerInputMode, peak_refresh_rate, charging, charge_limit, bt, screenrecord, rotation, airplane, dnd, cast, hotspot, screenshot, des tuiles d'AYN, battery, night, dark, inversion, location.

## Fonctionnement

### Le geste

1. À la connexion du service d'accessibilité, si le root d'AYN répond : `send-disable-flag statusbar-expansion`. Le volet d'Android ne se déploie plus.
2. Une bande tactile invisible de 20 dp couvre le haut de l'écran du haut : une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` pleine largeur, non focusable, `FLAG_NOT_TOUCH_MODAL`. Un glissement vers le bas d'au moins 40 dp ouvre le centre. Un simple toucher dans ces 20 dp est perdu pour l'appli en dessous.
3. Le rétablissement : `send-disable-flag none` à la déconnexion du service (`onDestroy`), avec l'action « Turn Wayfinder off » et quand le réglage du Hub le coupe (voir Sécurités). La bande est retirée en même temps.
4. Les notifications en surimpression (en haut de l'écran) restent celles d'Android : elles ne sont pas bloquées.
5. Une nouvelle action « Notifications » ouvre le centre ; elle s'attribue à un bouton dans les contrôles de Wayfinder et n'est liée à aucun bouton par défaut.

### Le panneau

- Une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` plein écran, focusable (le Retour d'Android et la manette y arrivent), titre `WayfinderNotifications`, sur l'écran du haut.
- À gauche, un voile léger (le jeu visible) : le toucher ferme le centre.
- À droite, le volet : 60 % de la largeur, toute la hauteur, coins de 20 dp à gauche, sur la carte de menu d'iiSU (`IisuLook.MENU_LIGHT` en clair, `surfaceModal` en sombre). Il glisse depuis la droite en 200 ms à l'ouverture comme à la fermeture.
- Dans le volet, de haut en bas :
  - les tuiles rapides, sur une ou deux rangées : Wi-Fi, Bluetooth, Ne pas déranger, Rotation, Mode avion, Localisation, Thème sombre, Veilleuse, Économie de batterie. Chaque tuile a son icône et son nom ; allumée, elle est remplie du dégradé de sélection, comme les modes de sonnerie du panneau du son. Leurs états et leurs commandes sont ceux des interrupteurs « Android » de `PanelShortcuts` ;
  - le titre « Notifications » et le bouton « Clear notifications » (pastille avec icône) ;
  - les cartes, de la plus récente à la plus ancienne : icône ronde de l'appli, nom de l'appli en gras et heure relative (`DateUtils.getRelativeTimeSpanString`, dans la langue du système), titre en gras, texte sur trois lignes au plus, puis les boutons d'action de l'appli en pastilles ;
  - sans notification : la cloche et « No notifications », comme l'état vide d'iiSU.
- On ferme en touchant le voile, par un glissement du volet vers le haut, avec Retour ou avec B.
- Thème, police et couleurs suivent iiSU comme le reste (`ThorGlassTheme`, `IisuLookSource`).

### Les notifications

- Wayfinder devient lecteur de notifications : un `NotificationListenerService` déclaré dans le manifeste. L'accès est accordé une fois par le root (`cmd notification allow_listener <composant>`), au premier lancement du service.
- Liste : les notifications actives. Le résumé d'un groupe est masqué quand ses notifications sont là. Les notifications silencieuses sont incluses.
- Toucher une carte : son `contentIntent` (puis la notification part si l'appli a mis `autoCancel`), et le centre se ferme.
- Glisser une carte sur le côté : `cancelNotification(key)`, seulement si elle est effaçable. Les notifications permanentes ne bougent pas, comme chez Android.
- « Clear notifications » : `cancelAllNotifications()`, qui laisse les permanentes.
- Boutons d'action : leur `actionIntent`. Ceux qui portent un `RemoteInput` (réponse écrite) sont masqués.

### À la manette

- La navigation des fenêtres de Wayfinder (`PadNav`, comme le panneau rapide) : la croix parcourt les tuiles, le bouton, les cartes et leurs boutons ; A ouvre ou bascule ; X efface la carte choisie ; Select deux fois efface tout (le même garde-fou de 2,5 s que le multitâche, dit sur le bouton lui-même) ; B ferme.

## Briques communes (pour le morceau 5)

- La carte de menu d'iiSU : la teinte de `VolumeBar.menuCard` passe dans `ui` et sert à la barre de volume, au centre de notifications et, ensuite, au centre de contrôle.
- La tuile à bascule (icône et nom ; allumée, remplie du dégradé de sélection) : les modes de sonnerie du panneau du son et les tuiles rapides du centre la partagent.
- Le bouton en pastille avec icône (« Clear notifications »).

## Sécurités

- Sans root : rien n'est bloqué, pas de bande ; le volet d'Android reste.
- Un réglage du Hub, « Notification center » (allumé par défaut), coupe le centre de Wayfinder et rend le volet d'Android, sans désinstaller.
- Si Wayfinder plante sans rétablir le volet, Android relance aussitôt son service d'accessibilité, qui rétablit puis rebloque. Seul cas sans aucun volet : Wayfinder désactivé et le Thor pas encore redémarré.
- Fenêtre impossible à ajouter : le volet d'Android est rétabli.

## Hors périmètre

- La réponse écrite depuis une carte.
- Les tuiles propres à AYN (style de manette, gâchettes, limite de charge…).
- L'écran du bas (le volet d'Android n'y existe pas).
- Le centre de contrôle (morceau 5).

## Vérification

- Tests unitaires :
  - le filtrage (résumé de groupe masqué quand ses notifications sont là, gardé seul sinon) ;
  - l'ordre (la plus récente d'abord) ;
  - ce qui est effaçable.
- Sur le Thor :
  - d'abord le blocage seul : avec `statusbar-expansion`, un glissement depuis le haut n'ouvre plus le volet d'Android ; `none` le rétablit ;
  - puis le geste sur la bande ;
  - ouvrir, effacer, tout effacer, un bouton d'action (une notification de test : `cmd notification post`) ;
  - chaque tuile ;
  - à la manette ;
  - en clair et en sombre ;
  - « Turn Wayfinder off » et le réglage du Hub rendent le volet d'Android.

## Risques

- Une appli en plein écran qui a des boutons tout en haut : les 20 dp de la bande prennent leurs touchers.
- Une mise à jour d'AYN qui changerait le volet : le blocage par `cmd statusbar` resterait, la commande étant celle d'Android.
- Les notifications des applis qui ne mettent ni titre ni texte : la carte montre le nom de l'appli seul.
