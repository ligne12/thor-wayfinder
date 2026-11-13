# Tout Wayfinder au style des réglages d'iiSU — conception (morceau 6)

Date : 2026-11-13. Fork privé `ligne12/thor-wayfinder`, pour un seul Thor Max (Android 13). Rien n'est publié.

## But

Le centre de contrôle a pris l'allure des réglages d'iiSU (morceau 5) ; le reste de Wayfinder garde l'allure d'origine,
du verre translucide sur un fond animé coloré, et détonne à côté. Tout Wayfinder prend l'allure des réglages d'iiSU, en
changeant les briques visuelles partagées plutôt que chaque page.

## Décisions prises (Thomas, 2026-11-13)

- Tout Wayfinder : le Hub, ses pages, les feuilles, la visite guidée, l'écran compagnon, les bulles (option « A »).
- L'accueil du Hub au style des réglages (maquette « option 1 »), pas au style de l'accueil d'iiSU.
- Un focus sobre partout, comme dans les réglages d'iiSU, y compris dans le centre de contrôle : plus de halo. Le dégradé
  des options allumées reste.
- Les cartes gardent le liseré du centre de contrôle.
- Le découpage des pages ne change pas : chaque réglage garde sa carte.

## Le fond

- Partout où Wayfinder dessine un fond plein écran (`GlassScreen`) : un fond uni, la carte de menu d'iiSU (`menuCard`) —
  `#F1F6F8` en clair, `#2E2F34` en sombre. Plus de fond animé coloré, plus de grille de points.
- Le Hub devient opaque : l'appli derrière lui n'est plus visible. Le flou demandé derrière les fenêtres devenues opaques
  (le Hub, l'écran compagnon, le centre de contrôle) est retiré : il ne se voit plus et coûte au GPU (défaut mineur du
  morceau 5).
- Les réglages « Glass » d'iiSU (opacité, flou, teinte) n'ont plus d'effet sur les surfaces de Wayfinder. Les réglages de
  Wayfinder qui n'en ont plus non plus (More › Appearance : le fond « What's behind / Aurora », la transparence, la
  réfraction) sont retirés de la page et de la recherche.

## Les cartes et les tuiles

- Toute surface de carte, de tuile, de bouton ou de pastille (`glassSurface`, `GlassPanel`, `FocusableGlass`…) devient
  pleine, comme les cartes du centre de contrôle : la surface des cartes (`surfaceModal` : blanche en clair, `#2E2F34` en
  sombre), sans voile translucide, avec son ombre courte, sa lueur intérieure et son liseré d'un dp.
- Les coins restent ceux de chaque élément (20 dp pour les cartes).
- Les options allumées et la sélection gardent leur dégradé.

## Le focus

- Une seule apparence, là où elle est dessinée aujourd'hui (`focusIndicator`), donc partout : le Hub et ses pages, les
  feuilles, le centre de contrôle, le centre de notifications, le multitâche, la barre de volume.
- L'élément qui a le focus fonce légèrement, comme la ligne choisie des réglages d'iiSU : environ 7 % de noir en clair
  (mesuré sur iiSU : `#E2E5E7` sur une carte `#F5F7F8`), environ 7 % de blanc en sombre (à mesurer sur les réglages
  sombres d'iiSU à l'implémentation).
- Plus de halo, plus d'anneau en dégradé, plus de grossissement. Un élément qui arrive au focus hors de l'écran défile
  toujours jusqu'à être visible.
- Une ligne qui contient un curseur ou un interrupteur fonce en entier, pas seulement son contrôle.
- Une option allumée (dégradé) qui a le focus fonce aussi ; une carte d'image (multitâche) aussi.
- Le réglage « Selection gradient animations » d'iiSU n'a plus d'effet dans Wayfinder.

## L'accueil du Hub

- En-tête : « Wayfinder » et « For the AYN Thor » ; les pastilles pleines « Ready » et « Search settings & features » ;
  Ko-fi en bouton rond.
- La bande d'état (manette, batterie, écrans, luminosité) sans carte : libellés et valeurs, comme les mesures en haut du
  centre de contrôle.
- Les 7 tuiles en cartes pleines, à la même place et de la même taille.

## Les pages

- En-tête : un bouton retour rond (flèche) au lieu de la pastille « Back B » ; B ramène toujours en arrière. Puis le titre
  et le sous-titre.
- Les titres de section à filet (déjà en place), les cartes de réglage pleines, un rond à chevron pour ouvrir une sous-page
  là où une ligne en ouvre une.
- Le découpage en cartes, l'ordre et le contenu des pages ne changent pas.

## Par-dessus les jeux

- Les bulles (`FocusCue`, en vues Android) et le compteur FPS : fond plein (la carte de menu), texte et coins d'iiSU,
  ajustés à la main.
- Le clavier et le Control Deck suivent les nouvelles couleurs sans être redessinés.

## Hors périmètre

- Réorganiser la navigation (pas de barre latérale comme dans iiSU) ou regrouper les réglages d'une section dans une seule
  carte.
- Les dessins faits à la main : courbe de l'égaliseur, test de manette, gabarits de la manette, roue de couleur.
- L'écran d'Arrange.
- Les points des curseurs des pages (retirés seulement dans le centre de contrôle, au morceau 1).

## Vérification

- La suite de tests existante reste verte ; une règle pure nouvelle (la teinte du focus selon le thème) a son test.
- Sur le Thor, par captures avant / après, en clair puis en sombre, en vérifiant avant chaque action que Thomas n'utilise
  pas la console : l'accueil du Hub ; la page Screens ; une page chargée (le remappage d'un jeu) ; une feuille ; le focus à
  la manette dans le centre de contrôle et dans le multitâche ; une bulle.

## Risques

- Le focus sobre est discret par nature (c'est le choix d'iiSU) : sur une page chargée, il se voit moins qu'avec le halo.
- Une page qui dessine sa propre surface sans passer par les briques partagées garde l'ancien style : l'inventaire en compte
  une douzaine (dont `SearchResultRow`, `PresetCard`, `FpsPreview`, l'éditeur de raccourcis) ; elles sont reprises une à
  une.
- Le Hub opaque : qui l'ouvrait par-dessus un jeu pour garder un œil dessus ne le voit plus.
