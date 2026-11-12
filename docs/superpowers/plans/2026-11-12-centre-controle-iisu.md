# Centre de contrôle aux réglages iiSU, notifications par le haut — plan de réalisation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Le centre de notifications descend du haut en volet pleine largeur (N2) ; le centre de contrôle prend la
disposition en sections des réglages iiSU, plein écran du bas (C3).

**Architecture:**
- Les briques iiSU de `ui/IisuPanel.kt` grandissent : la tuile à bascule apprend la disposition basse (icône et nom côte à
  côte), la valeur, l'état désactivé et une icône fournie par l'appelant ; un titre de section à filet s'ajoute. La règle de
  disposition de la tuile est une fonction pure, testée.
- `NotificationCenter.kt` ne change que de mise en page (volet du haut, grille de deux colonnes).
- `QuickPanelWindow.kt` remplace son fond de verre et ses cartes par la carte de menu, les titres de section et les tuiles
  iiSU ; `LevelControls.kt` donne aux lignes de niveau un titre et l'option sans points.

**Tech Stack:** Kotlin, Jetpack Compose (`LazyVerticalGrid`, `GridItemSpan`), JUnit 4, adb (`sendevent`, `screencap`,
`cmd notification post`).

**Spec:** `docs/superpowers/specs/2026-11-12-centre-controle-iisu-design.md`

## Global Constraints

- Branche `iisu-look`, commits en anglais, sans URL de session, finis par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` ;
  poussés sur `origin` (le fork) seulement, jamais sur `upstream`.
- Chaque changement porte le préfixe de commentaire `// iiSU fork:` comme le reste du fork.
- Couleurs, arrondis et police uniquement par le thème (`LocalGlass`, `menuCard`, `IisuLook`) ; aucune nouvelle dépendance.
- Libellés en anglais (langue de Wayfinder).
- Écran du bas : 537 × 468 dp. Tailles de la spec : en-tête 36 dp, titres de section 16 dp, tuiles des modes 36 dp, lignes de
  niveau 40 dp, raccourcis 64 dp ; espacement vertical 6 dp ; marges 12 dp (côtés) et 6 dp (haut, bas).
- Aucun point sur les curseurs du centre de contrôle.
- Tests : `./gradlew -q :app:testDebugUnitTest` (suite entière verte à chaque tâche). Compilation :
  `./gradlew -q :app:assembleDebug` pendant les tâches, `:app:assembleRelease` réinstallée à la fin ; `adb install -r`.
- Sur le Thor (adb `-s 192.168.1.75:5555`, `MSYS_NO_PATHCONV=1`) :
  - une action, une capture ; vérifier l'appli au premier plan avant tout geste ; ne jamais toucher GameNative ;
  - aucun geste de bord sur les réglages d'iiSU ;
  - panneau rapide : bouton AYN `sendevent /dev/input/event0 1 194 1` puis `0 0 0`, `1 194 0`, `0 0 0` ;
  - manette : `/dev/input/event9` (304 A, 305 B, 314 Select ; croix `3 16 ±1` / `3 17 ±1`) ;
  - centre de notifications : `input -d 0 swipe 960 5 960 600 300` ;
  - captures : `screencap -d 4630946441858561667` (haut), `-d 4630946482288158084` (bas) ;
  - le sombre se teste avec la tuile « Dark theme » du centre (sûre depuis b2de632), et le Thor revient en clair après ;
  - les notifications de test (`cmd notification post`) sont effacées après.

## Review Focus

1. Texte du système agrandi : le centre de contrôle défile au lieu de couper ; aucun nom de tuile ne déborde de sa tuile.
2. Thème sombre : cartes lisibles sur la carte de menu, texte et valeur lisibles sur une tuile allumée (dégradé).
3. Beaucoup de notifications : le volet s'arrête à 85 % de l'écran, la grille défile dedans, la poignée reste visible.
4. Manette : la sélection ne sort jamais de l'écran ; B replie les lignes de niveau ouvertes avant de fermer ; gauche et droite
   sortent d'un curseur à ses bouts.
5. Repli du panneau sur l'écran du haut (volet de 540 dp) : la même disposition reste utilisable, en défilant.

Chaque ligne a sa vérification sur le Thor dans la tâche qui possède le code (pas de règle pure à tester en dehors de la tâche 1).

---

### Task 1: Briques iiSU — tuile basse, valeur, icône fournie ; titre de section

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ui/IisuPanel.kt`
- Test: `app/src/test/java/app/wayfinder/ui/IisuPanelTest.kt` (nouveau)

**Interfaces:**
- Produces:
  - `enum class TileLayout { INLINE, TWO_LINES, NAME_VALUE, LARGE }`
  - `internal fun tileLayout(heightDp: Float, hasValue: Boolean): TileLayout`
  - `fun IisuToggleTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, height: Dp = 72.dp, description: String = label, value: String? = null, disabled: Boolean = false, iconContent: (@Composable (tint: Color) -> Unit)? = null, onClick: () -> Unit)`
  - `@Composable fun IisuSectionTitle(icon: ImageVector, title: String, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing test** `IisuPanelTest.tileLayout_followsHeightThenValue`

```kotlin
assertEquals(TileLayout.INLINE, tileLayout(36f, hasValue = false))
assertEquals(TileLayout.INLINE, tileLayout(47.9f, hasValue = true))     // under 48 dp: side by side, value dropped
assertEquals(TileLayout.TWO_LINES, tileLayout(64f, hasValue = false))   // a long name on two lines
assertEquals(TileLayout.NAME_VALUE, tileLayout(64f, hasValue = true))   // name on one line, value under it
assertEquals(TileLayout.LARGE, tileLayout(72f, hasValue = false))       // the sound panel's tiles, unchanged
```

- [ ] **Step 2: Run it** — `./gradlew -q :app:testDebugUnitTest --tests app.wayfinder.ui.IisuPanelTest`
  Expected: FAIL (compilation: `tileLayout` / `TileLayout` unresolved).

- [ ] **Step 3: Implement** `tileLayout` (bornes : < 48 → INLINE ; < 72 → NAME_VALUE avec valeur, sinon TWO_LINES ; sinon
  LARGE) and make `IisuToggleTile` draw by it:
  - INLINE : `Row`, icône 18 dp, 6 dp, nom en `labelLarge` sur une ligne ;
  - TWO_LINES : le rendu actuel des tuiles basses ;
  - NAME_VALUE : icône 22 dp, nom en `labelMedium` sur une ligne, valeur en `labelSmall` (`textSecondary`, ou `onAccent`
    sur une tuile allumée) ;
  - LARGE : le rendu actuel des tuiles de 72 dp ;
  - `iconContent` remplace l'`Icon` quand il est fourni (même teinte) ; `disabled` estompe le contenu à 50 % sans retirer
    le clic (l'action dit pourquoi, comme aujourd'hui) ; `description` reste la description d'accessibilité.
  - `IisuSectionTitle` : une ligne de 16 dp — icône 14 dp en `textSecondary`, 6 dp, titre en `labelLarge` `textPrimary`,
    8 dp, puis un filet de 1 dp (`textTertiary` à 35 %) qui remplit le reste.

- [ ] **Step 4: Run** `./gradlew -q :app:testDebugUnitTest` → la suite entière passe (37 tests) ;
  `./gradlew -q :app:assembleDebug` → BUILD OK. Les appelants actuels (barre de volume, centre de notifications) compilent
  sans changement.

- [ ] **Step 5: Commit** — `iiSU bricks: low inline tile, tile value and icon slot, section title`

### Task 2: Le centre de notifications descend du haut

**Files:**
- Modify: `app/src/main/java/app/wayfinder/NotificationCenter.kt` (`Center`, `Sheet`)

**Interfaces:**
- Consumes: `IisuToggleTile` (Task 1, paramètres par défaut), `menuCard`, `NotificationModel.visible`.

- [ ] **Step 1: `Center`** — un `BoxWithConstraints` plein écran : le voile sur tout l'écran (alpha `1 - out`, un toucher
  ferme), puis le volet en haut : `fillMaxWidth`, `heightIn(max = maxHeight * 0.85f)`, `graphicsLayer { translationY = -out * size.height }`,
  fond `menuCard(g)` en `RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)`, marge 20 dp. Même animation de 200 ms
  et même retrait de la fenêtre en fin de sortie qu'aujourd'hui.

- [ ] **Step 2: `Sheet`** — une `Column` : un `LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f, fill = false))`,
  12 dp d'écart, puis la poignée (40 × 5 dp, coins ronds, `textTertiary` à 50 %, centrée, 8 dp au-dessus). Dans la grille :
  - en pleine ligne (`span = { GridItemSpan(maxLineSpan) }`) : la rangée des 9 tuiles (`IisuToggleTile`, `weight(1f)`, 64 dp,
    10 dp d'écart) avec le glissement vers le haut de 80 dp qui ferme, comme aujourd'hui ; le titre « Notifications » et son
    bouton ; l'état vide ;
  - puis une carte par notification (`items(items, key = { it.key })`), remplies ligne par ligne.

- [ ] **Step 3: Vérifier** — suite verte, `assembleDebug` OK, installer ; poster 3 notifications de test ; ouvrir le centre ;
  capture du haut : volet pleine largeur collé en haut, 9 tuiles sur une rangée (« Do not disturb », « Battery saver » sur
  deux lignes, entiers), cartes sur deux colonnes, la plus récente en haut à gauche, poignée sous les cartes. Puis :
  - fermer par un glissement vers le haut sur les tuiles, rouvrir, fermer par B (`sendevent` 305), puis par un toucher sous le volet ;
  - 12 notifications de test : le volet s'arrête à 85 %, la grille défile, la poignée reste (Review Focus 3) ;
  - à la manette, la croix passe d'une colonne à l'autre ;
  - en sombre (tuile « Dark theme »), une capture, puis retour en clair ;
  - effacer les notifications de test.

- [ ] **Step 4: Commit** — `Notification center: a full-width sheet from the top, cards in two columns`

### Task 3: Le centre de contrôle en sections iiSU

**Files:**
- Modify: `app/src/main/java/app/wayfinder/LevelControls.kt` (`BrightnessSliders`, `VolumeSliders`, `LevelRow`)
- Modify: `app/src/main/java/app/wayfinder/QuickPanelWindow.kt` (`ScreenMode`, `PanelBody`, `PanelHeader`, `ScreenModeCard`,
  `LevelsCard`, `TilesGrid`, `TileView`, `NowPlaying`, `DetailsCard`, `MediaCard`)

**Interfaces:**
- Consumes: `IisuToggleTile(…, value, disabled, iconContent)`, `IisuSectionTitle`, `tileLayout` (Task 1) ; `menuCard`.
- Produces: `BrightnessSliders(compact: Boolean = false, collapsible: Boolean = compact, sideExit: Boolean = false, title: String? = null, dots: Boolean = true)`,
  et la même signature pour `VolumeSliders` ; `LevelRow(…, dots: Boolean = !compact, …)`.

- [ ] **Step 1: Lignes de niveau** — avec `title`, la ligne « Both » montre l'icône (`LightMode` / `VolumeUp`) et `title` au
  lieu de « Both screens » ; `dots` va jusqu'à `GlassSlider`. La note « Apps on the bottom screen play quieter… » n'apparaît
  que lignes ouvertes (`open`) : le Hub, jamais repliable, reste identique.

- [ ] **Step 2: Fond et en-tête** — `PanelBody` : `Box(fillMaxSize().background(menuCard(g)))` à la place de `GlassScreen` ;
  la colonne défilante garde l'ordre de `PanelCards`, marges 12/6 dp, espacement 6 dp. `PanelHeader` (36 dp) : l'heure
  (`titleLarge`), les mesures sans `GlassPanel` (même calcul de place), puis trois boutons ronds de 36 dp (`FocusableGlass`,
  rayon 18 dp) : Arrange (crayon ; en édition, la pastille « Done » d'aujourd'hui), Wayfinder (boussole), Fermer.

- [ ] **Step 3: Sections** — avant chaque carte, `IisuSectionTitle` : screens → `ViewAgenda` « Screens » ; levels → `Tune`
  « Levels » ; tiles → `Apps` « Shortcuts » ; now → `SportsEsports` « This app » ; details → `Memory` « Details » ; media →
  `MusicNote` « Media ». Les cartes (niveaux, jeu en cours, détails, média) : `glassSurface(g, RoundedCornerShape(20.dp), well = g.surfaceModal, veil = false)`
  à la place de `GlassPanel`.

- [ ] **Step 4: Screens** — `ScreenMode` devient « Both screens », « Top only », « Bottom only » (icônes `ViewAgenda`,
  `VerticalAlignTop`, `VerticalAlignBottom`) ; trois `IisuToggleTile` de 36 dp, 6 dp d'écart, `on` = le mode actuel, le
  `first` du panneau sur la première ; même action qu'aujourd'hui (fermeture avant « Top only »), description « Both screens on »,
  « Top screen only », « Bottom screen only ».

- [ ] **Step 5: Levels** — une carte, marges 14/4 dp : `BrightnessSliders(collapsible = true, title = "Brightness", dots = false)`,
  un filet de 1 dp, `VolumeSliders(collapsible = true, title = "Volume", dots = false)` ; `sideExit` par défaut (les lignes
  occupent toute la largeur).

- [ ] **Step 6: Shortcuts** — 4 par ligne, 6 dp d'écart : `IisuToggleTile(t.icon, t.label, t.on, Modifier.weight(1f), height = 64.dp, description = "${t.label}: ${t.value ?: ""}", value = t.value, disabled = t.disabled, iconContent = { tint -> TileIcon(t.icon, t.pkg, tint, 22.dp) }) { t.action() }` ;
  `TileView` disparaît ; le mode édition passe à `ShortcutGridEditor(columns = 4, cellHeight = 64.dp)`.

- [ ] **Step 7: Vérifier** — suite verte, `assembleDebug` OK, installer ; ouvrir le panneau (bouton AYN) ; capture du bas :
  tout tient sans défiler, chaque tuile à valeur la montre, aucun point sur les curseurs. Puis :
  - déplier et replier luminosité et volume (chevron), B replie avant de fermer (Review Focus 4) ;
  - la manette de haut en bas, gauche et droite au bout d'un curseur ;
  - Arrange : afficher « Media », capture (le panneau défile), remettre comme avant ;
  - texte agrandi (`settings put system font_scale 1.3`, capture, retour à la valeur lue avant) (Review Focus 1) ;
  - en sombre (tuile « Dark theme »), une capture, retour en clair (Review Focus 2) ;
  - le repli sur l'écran du haut n'est vérifiable qu'écran du bas éteint : `Top only` depuis le panneau, rouvrir, capture du
    haut, puis « Both screens » (Review Focus 5).

- [ ] **Step 8: Commit** — `Control center in iiSU's settings look: sections, setting rows, iiSU tiles`

### Fin

- [ ] Version finale (`assembleRelease`) installée, `git push origin iisu-look`, mémoire du projet à jour (morceau 5 fait).
