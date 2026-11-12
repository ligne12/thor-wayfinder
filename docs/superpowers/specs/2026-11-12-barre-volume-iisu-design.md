# Barre de volume au style iiSU — conception (morceau 2)

Date : 2026-11-12. Fork privé `ligne12/thor-wayfinder`, branche `iisu-look`. Suite du morceau 1 (langage visuel iiSU).

## But

Quand on appuie sur les touches de volume, la barre d'Android 13 (pastille blanche verticale à droite) est remplacée par une barre Wayfinder au style iiSU. Elle garde les fonctions de celle d'Android. iiSU n'a pas de barre de volume : elle est dessinée dans son langage, avec la même case de verre, le même liseré, le dégradé de sélection et la police d'iiSU.

## Décisions prises (Thomas, 2026-11-12)

- Verticale, au bord droit, centrée en hauteur : la place de la barre d'Android.
- Contenu d'Android : le bouton de mode sonnerie (sonnerie, vibreur, silencieux), la jauge du volume média et le « … ».
- « … » déploie la barre sur place en panneau latéral du son (pas le panneau rapide entier) : les trois modes en cases à icône, puis les volumes des deux écrans, du haut et du bas, en grands curseurs au style iiSU.
- Dessinée comme les menus d'iiSU : carte pleine, coins de 20 dp, piste de curseur unie #72C5E4 (mesurée dans ses réglages).
- Sur l'écran qui a la manette (le jeu).
- Au toucher, comme celle d'Android. Un toucher à côté va au jeu. La manette reste au jeu, et le « … » ouvre le panneau rapide, pilotable à la manette.

## Fonctionnement

1. Le service d'accessibilité reçoit déjà toutes les touches (`onKeyEvent`). Pour `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`, il règle lui-même le volume média (`adjustStreamVolume(STREAM_MUSIC, …, 0)`, sans `FLAG_SHOW_UI`) : Android n'affiche plus sa barre.
2. Il affiche la barre Wayfinder sur l'écran de la manette, ou met à jour celle déjà affichée.
3. Dans la barre :
   - le bouton du haut fait tourner le mode sonnerie : sonnerie, puis vibreur, puis silencieux (`AudioManager.ringerMode`) ;
   - glisser sur la jauge règle le volume média ;
   - « … » déploie la barre en panneau son, visible 5 s après le dernier toucher.
4. La barre disparaît en fondu 1,5 s après la dernière pression ou le dernier toucher.
5. La synchronisation existante du volume de l'écran du bas (`LinkedVolume`) suit d'elle-même : elle écoute les changements du volume média.
6. Les touches de volume simulées par les contrôles de jeu (`deck/VirtualInput.kt`) passent par le même chemin.

## Cas laissés à Android

- Écran éteint, appel en cours (`AudioManager.mode` différent de `MODE_NORMAL`) : la touche n'est pas interceptée.
- Volume changé par une autre appli : Android affiche sa barre si cette appli le demande.
- Si la fenêtre ne peut pas être ajoutée : le volume est quand même réglé, avec la barre d'Android (`FLAG_SHOW_UI`).
- Passer en silencieux exige l'accès « Ne pas déranger ». Sans cet accès, le bouton saute le silencieux (sonnerie ↔ vibreur).

## Apparence

- Case de verre `glassSurface` (voile, lueur, liseré, ombre du morceau 1), arrondi `Radii.Card`, environ 64 × 300 dp, à 16 dp du bord droit.
- En haut, le bouton de mode : icône `NotificationsActive`, `Vibration` ou `NotificationsOff`, dans une petite case de verre.
- Au centre, une piste capsule de 12 dp de large, remplie depuis le bas avec le dégradé d'accent (`accentStops`), et l'icône `VolumeOff` / `VolumeDown` / `VolumeUp` au pied de la piste. La forme suit la barre d'Android.
- Sous la piste, le pourcentage, en `labelLarge` (la police d'iiSU).
- En bas, « … » (`MoreHoriz`).
- Thème clair ou sombre comme le panneau (suit iiSU).

## Hors périmètre

- Remplacer la barre d'Android quand une autre appli change le volume.
- Le volume de l'écran du bas dans la barre : il est dans le panneau rapide, derrière « … ».
- La navigation à la manette dans la barre.

## Vérification

- Tests unitaires : niveau → pourcentage, niveau → icône, mode sonnerie suivant (avec et sans accès « Ne pas déranger »).
- Sur le Thor :
  - appuis réels par `sendevent` sur `gpio-keys` (codes 114 et 115) : la barre Wayfinder apparaît, celle d'Android non, le volume change, l'écran du bas suit, la barre disparaît après 1,5 s ;
  - toucher du bouton de mode, de la jauge et de « … » ;
  - en clair et en sombre, avec un jeu sur l'écran du haut puis sur celui du bas.

## Risques

- Une mise à jour d'AYN qui filtrerait les touches de volume avant le service d'accessibilité : la barre d'Android reviendrait, sans casse.
- Un appui maintenu : chaque répétition change le volume, comme chez Android.
