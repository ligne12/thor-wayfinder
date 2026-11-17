# Appui long sur les tuiles, et la barre de son — conception

Date : 2026-11-16. Fork privé `ligne12/thor-wayfinder`, branche `iisu-look`. Suite des morceaux iiSU (barre de son,
centre de contrôle, centre de notifications).

## Pourquoi

- **La barre de son.** Le grand panneau « … » reste 5 s après le dernier toucher, et rien ne le ferme quand on retouche
  le jeu : il gêne.
- **Les tuiles.** Aujourd'hui, chaque tuile est un simple bouton : un appui bascule, ou passe à la valeur suivante. C'est
  bien, mais quand c'est pertinent Thomas veut un comportement à la iOS : maintenir la tuile ouvre une fenêtre avec les
  options importantes. Plus besoin d'aller dans les réglages d'Android, ni d'appuyer trois fois pour atteindre une
  valeur.

## Décisions prises (Thomas, 2026-11-16)

1. Barre de son : le grand panneau reste **4 s** (au lieu de 5) après le dernier toucher, et **un toucher hors de la
   barre la ferme**, petite ou grande. Ce toucher va quand même au jeu, comme avec la barre d'Android.
2. L'appui court ne change nulle part. Sur une tuile à valeurs : **court = valeur suivante, long = choisir la valeur**.
3. Ouverture : **doigt maintenu 0,4 s**, une petite vibration, et la fenêtre s'ouvre **au centre** (la fenêtre de
   « App pairs »). À la manette : **A maintenu 0,5 s** ; A court fait ce qu'il fait aujourd'hui.
4. Les 14 tuiles de la liste ci-dessous, avec les deux ajouts : la correction des haut-parleurs et les lumières des
   sticks.
5. Aux deux endroits où les tuiles sont affichées : la grille « Shortcuts » du centre de contrôle et la rangée du
   centre de notifications (4 de ses 9 tuiles sont concernées : Wi-Fi, Bluetooth, Do not disturb, Night light).
6. Sur une tuile sans options, l'appui long ne fait rien, sans vibration. Android y ouvrirait sa page de réglages, ce
   qui fermerait le panneau en plein jeu.

## Buts, non-buts, anti-buts

**Buts.** Les 14 fenêtres ci-dessous, au toucher et à la manette, dans les deux centres. La barre de son plus discrète.

**Non-buts.**
- Pas d'appui long sur les autres tuiles (liste C).
- Pas de saisie de mot de passe Wi-Fi, pas d'association d'un nouvel appareil Bluetooth : la page d'Android s'en charge.
- Pas de sélecteur de couleur dans la fenêtre des lumières : la page Lumières l'a déjà.
- Pas de programmation horaire (Night light, durée de Do not disturb).
- Pas de bulle accrochée à la tuile, pas de flou derrière la fenêtre (le look iiSU n'en a pas).

**Anti-buts** (même si tout marche par ailleurs, c'est raté si) :
- l'appui court d'une tuile change de comportement ;
- un appui long déclenche aussi l'action de l'appui court ;
- la fenêtre contourne une règle existante : ventilateur jamais sur Off, ClusterTune et Pulse qui gardent le processeur,
  « Keep for <jeu> » qui retient ce qui a été changé dans le panneau, Battery saver refusé en charge ;
- ouvrir ou fermer la fenêtre ferme le panneau, ou rend la manette au jeu.

## Approches

- **Retenue : chaque tuile porte ses options.** `PanelTile` gagne des options facultatives, sous deux formes : une
  **liste de choix** (un seul composant partagé par les tuiles à valeurs) ou un **contenu sur mesure** (curseurs,
  listes de réseaux ou d'appareils). Les options sont construites dans `PanelShortcuts.tiles`, à côté de l'action
  courte : la fenêtre et l'appui court lisent et écrivent la même valeur, et les règles existantes suivent d'elles-mêmes.
- Écartée : une fenêtre écrite à part par tuile. 14 fenêtres, dont 7 presque identiques, et la valeur lue à deux
  endroits qui finissent par diverger.
- Écartée : une bulle accrochée à la tuile, comme sur iOS. Thomas a choisi le centre, et la bulle obligerait à gérer sa
  position près des bords des deux écrans.

## Fonctionnement

### 1. La barre de son (`VolumeBar.kt`)

- `HIDE_WIDE_MS` passe à 4000.
- La fenêtre de la barre ajoute `FLAG_WATCH_OUTSIDE_TOUCH`. Dans `dispatchTouchEvent` de sa racine, un
  `MotionEvent.ACTION_OUTSIDE` lance `fadeOut()` (sans `bump()`). Rien n'est consommé : le jeu reçoit son toucher
  (`FLAG_NOT_TOUCH_MODAL` reste).
- Android n'envoie `ACTION_OUTSIDE` que pour un toucher sur le même écran : un toucher sur l'autre écran laisse la barre
  aller au bout de son délai. C'est accepté.
- Le KDoc de `VolumeBar` dit « 5 s when widened » : il passe à 4 s et mentionne le toucher dehors.

### 2. L'appui long sur une tuile

- **`PanelTile`** gagne `options` (null : pas d'appui long). Deux formes :
  - **choix** : un titre, des choix (un mot, choisi ou non, éventuellement grisé), ce que fait un choix, et au besoin une
    ligne de plus sous la liste : un lien (« Fan curve… ») ou un interrupteur (« reste où je l'envoie ») ;
  - **sur mesure** : un titre et un contenu composable.
- **État** : la tuile dont la fenêtre est ouverte, et le centre qui l'a ouverte. Le centre de contrôle (écran du bas)
  et le centre de notifications (écran du haut) peuvent être ouverts en même temps : une fenêtre ouverte dans l'un ne
  s'affiche pas dans l'autre. L'état est remis à zéro par `startPanel` pour le centre de contrôle, et à la fermeture du
  centre de notifications pour le sien. *(Choix du concepteur.)*
- **`IisuToggleTile`** gagne `onLongClick: (() -> Unit)?`. Sans lui, la tuile se comporte exactement comme aujourd'hui :
  c'est le cas des autres tuiles qui l'utilisent (les modes d'écran du centre de contrôle, les modes de sonnerie de la
  barre de son).
- **Tuiles du panneau sans options (liste C)** : un appui maintenu (doigt 0,4 s, A 0,5 s) n'agit plus du tout, ni au
  relâchement ni avec une vibration (décision 6). Aujourd'hui, maintenir puis relâcher déclenche l'action, au doigt
  comme à la manette : maintenir « Sleep » en cherchant des options endormirait le Thor. L'exception reste Performance
  avec ClusterTune ou Pulse, dont l'appui long montre la même pastille que l'appui court.
  - Toucher : `detectTapGestures(onLongPress = …)`, donc le délai d'Android (400 ms). Un glissement annule l'appui long
    (la rangée du centre de notifications se tire vers le haut pour fermer : ça doit continuer de marcher).
  - Vibration à l'ouverture : `LocalHapticFeedback` (`HapticFeedbackType.LongPress`).
  - Manette : `ReleaseGate` apprend l'appui long. A tenu 500 ms : la fenêtre s'ouvre, et le relâchement qui suit ne fait
    rien. A relâché avant : l'action courte, au relâchement, comme aujourd'hui. Un relâchement annulé ne fait toujours
    rien.
- **La fenêtre** : `GlassSheet` avec `ModalHeader` (le nom de la tuile, la croix). Elle s'ouvre sur le choix actuel (ou
  le premier contrôle). B, un toucher dehors ou la croix la ferment, et le focus revient à la tuile (déjà le cas de
  `GlassSheet`). Elle est dessinée comme « App pairs » : même largeur (0,92 de la fenêtre), même carte.
- **Après un choix** : dans une liste de choix, un toucher ou A applique le choix et ferme la fenêtre (comme le menu
  d'iOS). Une fenêtre sur mesure reste ouverte : on y règle plusieurs choses ou on fait glisser un curseur. La tuile se
  relit comme après un appui court (le `refresh` existant).
- **Centre de contrôle** (`QuickPanelWindow.TilesGrid`) : passe `onLongClick` aux tuiles qui ont des options. La fenêtre
  est affichée à côté de `PanelPairsDialog` et `PanelShotSheet`.
- **Centre de notifications** : il n'a pas de `WindowRoot`, et sa racine prend B et Back pour se fermer avant que Compose
  ne les voie. Fenêtre ouverte, B et Back doivent fermer la fenêtre seulement. La fenêtre couvre tout le centre (pas
  seulement la rangée de tuiles), donc elle est dessinée au niveau de la `Box` racine de `Center`, pas dans la grille.

### 3. Le contenu de chaque fenêtre

**A. Fenêtres de réglages** (appui court = allumer ou éteindre, comme aujourd'hui).

| Tuile | Contenu | Lecture, écriture, règles |
|---|---|---|
| Wi-Fi (`wifi`) | Interrupteur ; le réseau actuel ; les réseaux enregistrés à portée ; « Wi-Fi settings… » | L'appli n'a pas la localisation : `WifiManager` donne `<unknown ssid>`. Le nom vient de `cmd wifi status` par le root (`PServiceBridge`), les réseaux de `cmd wifi list-networks` croisé avec `cmd wifi list-scan-results`. Toucher un réseau enregistré s'y connecte si une commande root le permet sur le Thor (à vérifier en premier) ; sinon il ouvre la page Wi-Fi d'Android. Le lien : `QuickSettings.openSettings(ACTION_WIFI_SETTINGS)` (écran du haut, le panneau se ferme). |
| Bluetooth (`bt`) | Interrupteur ; les appareils associés, les connectés en tête avec « Connected » ; « Bluetooth settings… » | Lecture : `BluetoothAdapter.bondedDevices` et l'état de connexion (permission `BLUETOOTH_CONNECT`, à déclarer et à accorder par le root `pm grant`), ou `dumpsys bluetooth_manager` par le root. Connecter ou déconnecter d'un toucher est réservé aux applis système par Android : à essayer sur le Thor, sinon un toucher ouvre la page Bluetooth d'Android. |
| Do not disturb (`dnd`) | Liste de choix : Off, Priority only, Alarms only, Total silence | Lecture `zen_mode` (0 Off, 1 Priority, 3 Alarms, 2 Total silence). Écriture `cmd notification set_dnd off / priority / alarms / none`, comme la tuile. |
| Night light (`night`) | Interrupteur ; un curseur d'intensité (à droite : plus chaud) | `night_display_color_temperature` en kelvins, écrit par le root (`settings put secure`). Bornes du Thor à lire (`config_nightDisplayColorTemperatureMin/Max`), à défaut celles d'AOSP (2596 à 4082 K). |
| Extra dim (`dim`) | Interrupteur ; un curseur d'intensité | `reduce_bright_colors_level`, de 0 à 100, écrit par le root. |
| Speaker sound fix (`speaker`, et `speakereq` qui ouvre la même) | Interrupteur de la correction ; égaliseur on/off (grisé si la correction est éteinte, comme la tuile « Speaker EQ ») ; Volume boost Off, +6 dB, +12 dB ; largeur stéréo (curseur de 1 à 3) | `SpeakerTune.setOn`, `setEq`, `setVolumeBoost`, `setStereoWidth`. |
| Lights on/off (`lightsonoff`) | Liste de choix : les effets (AYN's own, Off, Colour, Breathing, Strobe, Spectrum, Screen colour) ; « Light settings… » | Un effet garde les couleurs, la vitesse et la luminosité du profil global. Off mémorise le profil (`lights_before_off`), comme `QuickSettings.toggleStickLights` ; un effet choisi depuis Off part du profil mémorisé. Écriture : `LightSettings.setGlobalProfile` puis `StickLights.apply`. Le lien ouvre la page Lumières (`HubPage.LIGHTS`). |

**B. Tuiles à valeurs** (appui court = valeur suivante, comme aujourd'hui ; appui long = liste de choix).

| Tuile | Choix | Règles à garder |
|---|---|---|
| Performance (`perf`) | Standard, Medium, High | Comme `QuickSettings.cyclePerformance` (`performance_mode` et `persist.vendor.debug.mode`), puis `touch("perf")`. Avec ClusterTune ou Pulse, l'appui long fait comme l'appui court : la pastille, pas de fenêtre. |
| Fan (`fan`) | Quiet, Smart, Sports, Custom ; « Fan curve… » | Jamais Off. `PerfProfiles.markOwn(v)`, puis `touch("fan")`. Le lien : `QuickSettings.openFanCurve`. |
| Controller lock (`lock`) | Off (« Unlocked » si l'interrupteur est actif), Top screen, Bottom screen ; l'interrupteur « Stays where you send it » | Les mots de `PanelRules.lockValue`. `AppSettings.setFocusLock`, `setFocusStickyOn`. Appliqué quand le panneau se ferme, comme aujourd'hui. |
| Frame rate (`fps`) | Off, puis `PanelRules.FPS_LEVELS` | `setFpsCounterOn`, `setFpsLevelTo`, `reapplyFps`, et la pastille « The counter shows over games » une fois par panneau, comme la tuile. |
| Volume boost (`boost`) | Off, +6 dB, +12 dB | `SpeakerTune.setVolumeBoost`. |
| Face buttons (`style`) | Nintendo, Xbox, PlayStation | `ButtonNames.setThorStyle`. Si le contrôleur d'AYN est coupé (`temp_abxy_layout_mode` = 2), aucun n'est coché. |
| L2 / R2 mode (`trigger`) | Analog, Digital, Both | `settings put system trigger_input_mode 0 / 1 / 2`. |

**C. Pas d'appui long** : toutes les autres tuiles (interrupteurs à deux positions, actions, « Screenshot » et
« App pairs » qui ouvrent déjà une fenêtre, tuiles « Ouvrir… »).

## Laissé au constructeur

- Les noms des types et la forme exacte des options.
- Comment détecter A tenu : les répétitions de touche ou un minuteur lancé à l'appui (le Thor décidera).
- La lecture du Bluetooth : l'API avec la permission, ou `dumpsys` par le root.
- L'ordre des réseaux et des appareils, et ce que la fenêtre affiche pendant la lecture (les commandes root prennent un
  moment).
- Comment le centre de notifications donne B et Back à la fenêtre ouverte (un `WindowRoot`, ou un test dans sa racine).

## Vérification

- **Tests JVM :**
  - l'appui long de `ReleaseGate` : relâché avant 500 ms, l'action courte ; tenu 500 ms, les options, et le relâchement
    ne fait rien ; tenu 500 ms sur une tuile sans options, rien ; annulé, rien ;
  - `zen_mode` vers le choix coché ;
  - la position du curseur Night light vers les kelvins, dans les deux sens.
- **Sur le Thor, d'abord :** les commandes Wi-Fi et Bluetooth (connexion à un réseau enregistré, à un appareil associé),
  pour fixer ce que les deux fenêtres font d'un toucher.
- **Sur le Thor, ensuite :**
  - chaque tuile des listes A et B, au toucher et à la manette, dans les deux centres, en clair et en sombre ;
  - l'appui court de chaque tuile reste le même ; un appui maintenu sur une tuile de la liste C ne fait plus rien ;
  - la rangée du centre de notifications se tire toujours vers le haut ;
  - « Keep for <jeu> » après un choix de performance ou de ventilateur ;
  - la barre de son : le grand panneau part après 4 s, un toucher sur le jeu la ferme et le jeu le reçoit.

## Risques

- Le Wi-Fi et le Bluetooth : se connecter d'un toucher peut être impossible sans droits système. Le repli est la page
  d'Android.
- La manette : si le Thor n'envoie pas de répétitions pour A tenu, il faudra un minuteur.
- La barre de son : un toucher sur l'autre écran ne la ferme pas (limite d'Android).
