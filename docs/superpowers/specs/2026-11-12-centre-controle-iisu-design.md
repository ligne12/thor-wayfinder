# Centre de contrôle aux réglages iiSU, centre de notifications par le haut — conception (morceau 5)

Date : 2026-11-12. Fork privé `ligne12/thor-wayfinder`, pour un seul Thor Max (Android 13). Rien n'est publié.

## But

Le centre de contrôle (le panneau rapide de Wayfinder, bouton AYN, écran du bas) prend la disposition et l'apparence des
réglages d'iiSU, pour ne plus détonner à côté du centre de notifications, de la barre de volume et d'iiSU. Le centre de
notifications, lui, descend du haut de l'écran au lieu d'arriver par la droite.

## Décisions prises (Thomas, 2026-11-12)

- Tout reste visible d'un coup dans le centre de contrôle, sans défilement, avec les cartes par défaut.
- Maquettes choisies : **N2** (centre de notifications en volet pleine largeur qui descend du haut) et **C3** (centre de
  contrôle en sections iiSU empilées, plein écran du bas).
- Les tuiles qui ont une valeur la gardent sous leur nom (amélioration de C3, acceptée).
- Pas de points sur les curseurs du centre de contrôle (demande antérieure de Thomas, maintenue).
- Le volet ne suit pas le doigt pendant qu'on tire ; l'écran d'Arrange n'est pas redessiné.

## Partie A — le centre de notifications descend du haut

Seule la mise en page de `NotificationCenter.kt` change. La fenêtre, le geste, le blocage du volet d'Android, la lecture des
notifications, les commandes des tuiles et la manette restent ceux du morceau 4.

- Le volet occupe toute la largeur de l'écran du haut, collé au bord du haut, coins de 20 dp en bas, sur la carte de menu
  d'iiSU (`menuCard`). Sa hauteur suit son contenu, au plus 85 % de l'écran ; au-delà, la liste défile à l'intérieur.
- Il glisse depuis le haut en 200 ms à l'ouverture comme à la fermeture (aujourd'hui depuis la droite). Une petite poignée
  centrée en bas du volet dit qu'il se referme vers le haut.
- Sous le volet, le voile léger : un toucher ferme le centre.
- Dans le volet, de haut en bas :
  - les 9 tuiles rapides sur une seule rangée (`IisuToggleTile`, 64 dp), noms complets, sur deux lignes au besoin ;
  - le titre « Notifications » et le bouton « Clear notifications » ;
  - les cartes sur deux colonnes, remplies ligne par ligne, la plus récente en haut à gauche ; l'état vide (« No
    notifications ») centré sur les deux colonnes.
- On ferme avec B, Retour, Home, un toucher sur le voile, ou un glissement vers le haut d'au moins 80 dp sur les tuiles ou le
  titre (la liste, elle, défile).
- À la manette, la croix se déplace aussi entre les deux colonnes.

## Partie B — le centre de contrôle en sections iiSU

La fenêtre (`QuickPanelWindow`, écran du bas, repli sur l'écran du haut) et toutes les fonctions restent. La mise en page de
`PanelBody` change.

### Fond et cartes

- Le fond de tout le panneau : la carte de menu d'iiSU (`menuCard`), claire ou sombre selon iiSU. Le verre coloré animé
  (`GlassScreen`) n'est plus dessiné derrière.
- Les cartes : la surface des cartes du centre de notifications (blanche en clair, sombre en sombre), coins de 20 dp.

### De haut en bas

1. **En-tête** (36 dp) : l'heure ; les mesures CPU, GPU, RAM, batterie sur une ligne, sans carte, avec la même règle
   qu'aujourd'hui quand la place manque (valeurs raccourcies, puis la RAM s'efface) ; puis trois boutons ronds : Arrange
   (crayon ; « Done » en mode édition), Wayfinder (boussole), Fermer.
2. **Titres de section** (16 dp) : icône, nom, puis un filet d'un pixel jusqu'au bord, comme « Launch Behavior » dans iiSU.
   Une nouvelle brique `IisuSectionTitle(icon, title)` dans `ui/IisuPanel.kt`.
3. **Screens** : les trois modes en tuiles basses (36 dp), icône et nom côte à côte ; allumée, la tuile est remplie du dégradé
   de sélection. Règle de la brique `IisuToggleTile` : sous 48 dp de haut, l'icône et le nom sont côte à côte.
4. **Levels** : une carte avec deux lignes de réglage de 40 dp, séparées par un filet :
   - icône, « Brightness » ou « Volume », le curseur iiSU (sans points), la valeur, le chevron rond ;
   - le chevron déplie sous la ligne « Top screen » et « Bottom screen », dans la même carte (B les replie d'abord, comme
     aujourd'hui).
5. **Shortcuts** : les raccourcis choisis, dans l'ordre d'Arrange, 4 par ligne, tuiles de 64 dp à l'apparence
   d'`IisuToggleTile` (forme, fond, dégradé quand allumée, bande de focus). Elles gardent leur icône (l'icône de l'appli pour un
   raccourci d'appli), leur état désactivé (estompé) et leur valeur :
   - avec une valeur, le nom sur une ligne et la valeur dessous ;
   - sans valeur, le nom sur deux lignes au besoin.

Les cartes en option d'Arrange (jeu en cours, détails, média) prennent le même style : un titre de section et une carte.
Elles s'insèrent dans l'ordre choisi. Si tout ne tient pas (cartes en option affichées, gros texte), le panneau défile.

### Ce qui ne change pas

- Arrange : le choix et l'ordre des cartes et des raccourcis, le mode édition sur place (cellules de 64 dp pour suivre les
  tuiles). Il prend les nouvelles couleurs sans être redessiné.
- Les fenêtres ouvertes depuis le panneau (paires d'applis, choix de l'écran de capture).
- La manette : de haut en bas, première sélection sur le premier mode d'écran ; gauche et droite règlent le curseur
  sélectionné et en sortent à ses bouts ; B replie les lignes ouvertes, puis ferme.

## Hors périmètre

- Le volet de notifications qui suit le doigt.
- Une refonte de l'écran d'Arrange, du Hub et des autres pages de Wayfinder.
- Les tuiles propres à AYN absentes aujourd'hui.

## Vérification

- Pas de nouvelle règle pure : la suite de tests existante reste verte.
- Sur le Thor, par captures, en clair puis en sombre :
  - centre de notifications : l'ouverture par le haut, la fermeture de chaque façon, les deux colonnes avec plusieurs
    notifications de test, l'état vide, la manette entre les colonnes ;
  - centre de contrôle : tout tient sans défilement avec les cartes par défaut ; chaque tuile garde sa valeur ; déplier et
    replier luminosité et volume ; la manette de haut en bas ; Arrange (ajout, ordre, retour) ; une carte en option affichée
    (le panneau défile) ; le repli sur l'écran du haut.

## Risques

- La hauteur : 468 dp pour l'écran du bas. Avec le texte agrandi, les tuiles ne tiennent plus et le panneau défile.
- Neuf tuiles sur une rangée : environ 80 dp chacune ; un nom long (« Battery saver ») passe sur deux lignes, sans coupure.
- Deux colonnes de cartes : une notification longue rend sa ligne plus haute que sa voisine.
