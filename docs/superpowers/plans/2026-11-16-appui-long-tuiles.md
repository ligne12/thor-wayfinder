# Appui long sur les tuiles, et la barre de son — plan de réalisation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Maintenir une tuile du centre de contrôle ou du centre de notifications ouvre une fenêtre d'options (14 tuiles) ; la barre de son se ferme au toucher dehors et son grand panneau part après 4 s.

**Architecture:** `PanelTile` gagne `options` (une liste de choix ou un contenu sur mesure), construites dans `PanelShortcuts.tiles` à côté de l'action courte. `IisuToggleTile` apprend l'appui long (doigt : `onLongPress` ; manette : A tenu 500 ms, par un minuteur et `ReleaseGate.held`). Chaque centre garde la tuile dont la fenêtre est ouverte et l'affiche dans une `GlassSheet` ; le centre de notifications reçoit pour cela un `WindowRoot` et un répartiteur de Back, comme le centre de contrôle.

**Tech Stack:** Kotlin, Jetpack Compose, `PServiceBridge` (root), `WindowManager`, JUnit 4, adb.

**Spec:** `docs/superpowers/specs/2026-11-16-appui-long-tuiles-design.md`

## Global Constraints

- Branche `iisu-look`, commits en anglais, finis par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Fork privé : aucun `git push` vers `upstream`, aucune publication d'APK.
- L'appui court de chaque tuile ne change pas. Un appui long ne déclenche jamais aussi l'action courte.
- Tuiles du panneau sans options (liste C) : un appui maintenu (doigt 0,4 s, A 0,5 s) ne fait rien, sans vibration.
- Règles gardées : ventilateur jamais sur Off ; avec ClusterTune ou Pulse, Performance ne change rien et montre sa pastille (aussi à l'appui long) ; `touch("perf" / "fan")` après un choix (« Keep for <jeu> ») ; `PerfProfiles.markOwn` pour le ventilateur.
- Couleurs et arrondis uniquement par le thème (`LocalGlass`, `Radii`) ; textes à l'écran en anglais, comme le reste de l'appli.
- Fenêtre d'options : `GlassSheet`, `Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp)` (0,92 comme « App pairs », plafonnée pour l'écran du haut, plein écran), `contentPadding = 0.dp`, `ModalHeader(<nom de la tuile>, onClose)`.
- Liste de choix : un choix applique et ferme la fenêtre. Fenêtre sur mesure : reste ouverte.
- Compilation et installation : `./gradlew :app:assembleRelease` puis `adb install -r app/build/outputs/apk/release/app-release.apk`. Tests : `./gradlew :app:testDebugUnitTest --tests "<classe>"`.
- Sur le Thor : une action, une capture. Si le Thor n'est pas branché (`adb devices` vide), les étapes sur l'appareil attendent : le dire, ne pas les cocher.

## Review Focus

1. **Glisser la rangée de tuiles du centre de notifications vers le haut** ferme toujours le centre, et n'ouvre jamais une fenêtre. Vérification : tâche 3, étape 6.
2. **A maintenu, puis la fenêtre du panneau perd le focus** (bouton AYN, la manette change d'écran) : ni fenêtre, ni action. Test `held_afterCancelledRelease_doesNothing` et règle « focus perdu = minuteur annulé » (tâche 2).
3. **Le centre de contrôle et le centre de notifications ouverts en même temps** : une fenêtre ouverte dans l'un n'apparaît pas dans l'autre, et B ne ferme que la fenêtre de son centre. Vérification : tâche 3, étape 6.
4. **Une valeur changée ailleurs pendant que la fenêtre est ouverte** (ventilateur changé par le profil d'un jeu, Wi-Fi coupé par le mode avion) : la fenêtre montre la valeur relue, pas l'ancienne. Les choix sont reconstruits à chaque relecture des tuiles (`tick` / `refresh`) ; vérification tâche 4, étape 5.
5. **Commande root lente ou sans réponse** (`cmd wifi …`, `settings get …`) : la fenêtre s'ouvre tout de suite, avec « Reading… », jamais bloquée sur le fil principal. Tâche 6, étape 4.

---

### Task 1: La barre de son se ferme au toucher dehors, et son grand panneau après 4 s

**Files:**
- Modify: `app/src/main/java/app/wayfinder/VolumeBar.kt` (KDoc l. 56-63, `HIDE_WIDE_MS` l. 66, `dispatchTouchEvent` l. 165, drapeaux l. 191-192)

**Interfaces:** aucune (changement interne).

- [ ] **Step 1:** `HIDE_WIDE_MS = 4000L`. Ajouter `FLAG_WATCH_OUTSIDE_TOUCH` aux drapeaux de la fenêtre. Dans `dispatchTouchEvent` de la racine : `if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { fadeOut(); return false }`, avant `bump()`. Le KDoc dit « 4 s when widened » et « a touch outside it fades it out (the touch still goes to the game) ».
- [ ] **Step 2:** `./gradlew :app:assembleRelease`, installation. Sur le Thor, un jeu au premier plan : Volume + (`adb shell sendevent` codes 115, comme le plan de la barre), toucher le jeu (`adb shell input tap` hors de la barre) : la barre part en fondu, le jeu réagit au toucher. « … » : le grand panneau part 4 s après le dernier toucher. Capture à chaque fois.
- [ ] **Step 3:** Commit `Volume bar: an outside touch closes it, the sound panel goes after 4 s`.

### Task 2: L'appui long d'une tuile (doigt et manette)

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ui/GlassComponents.kt:153-174` (`ReleaseGate`)
- Modify: `app/src/main/java/app/wayfinder/ui/IisuPanel.kt:72-129` (`IisuToggleTile`)
- Test: `app/src/test/java/app/wayfinder/ui/ReleaseGateTest.kt` (nouveau)

**Interfaces:**
- Produces: `ReleaseGate.held(long: () -> Unit)` et `ReleaseGate.LONG_MS = 500L` ; `IisuToggleTile(icon: ImageVector?, …, onLongClick: (() -> Unit)? = null, onClick: () -> Unit)` — `icon` devient nullable (sans icône ni `iconContent`, seul le nom est dessiné).

- [ ] **Step 1: Écrire les tests**

```kotlin
class ReleaseGateTest {
    @Test fun release_beforeHold_runsTheShortAction() {
        val g = ReleaseGate(); var short = 0
        g.press(0); g.release(canceled = false) { short++ }
        assertEquals(1, short)
    }
    @Test fun held_runsTheLongAction_andTheReleaseDoesNothing() {
        val g = ReleaseGate(); var short = 0; var long = 0
        g.press(0); g.held { long++ }; g.release(canceled = false) { short++ }
        assertEquals(1, long); assertEquals(0, short)
    }
    @Test fun held_withANoOp_swallowsTheRelease() {          // a tile of list C
        val g = ReleaseGate(); var short = 0
        g.press(0); g.held { }; g.release(canceled = false) { short++ }
        assertEquals(0, short)
    }
    @Test fun held_afterCancelledRelease_doesNothing() {
        val g = ReleaseGate(); var short = 0; var long = 0
        g.press(0); g.release(canceled = true) { short++ }; g.held { long++ }
        assertEquals(0, short); assertEquals(0, long)
    }
    @Test fun longPressTimeout_isHalfASecond() = assertEquals(500L, ReleaseGate.LONG_MS)
}
```

- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "app.wayfinder.ui.ReleaseGateTest"` : échec (`held` et `LONG_MS` n'existent pas).
- [ ] **Step 3: `ReleaseGate.held`.** `fun held(long: () -> Unit) { if (armed) { armed = false; long() } }` et `const val LONG_MS = 500L` dans le `companion object`. `onKey` ne change pas (ses autres appelants : `FocusableGlass`, les cartes de notifications).
- [ ] **Step 4: `IisuToggleTile`.**
  - Toucher : `pointerInput(onLongClick != null)` avec `detectTapGestures(onLongPress = if (onLongClick != null) { _ -> long() } else null) { …tap d'aujourd'hui… }`, où `long` vient de `rememberUpdatedState(onLongClick)`. Sans `onLongClick`, le geste reste celui d'aujourd'hui.
  - Manette : avec `onLongClick`, dans `onKeyEvent`, sur un `KeyDown` de sélection (`ReleaseGate.isSelect`) au `repeatCount == 0`, lancer `scope.launch { delay(ReleaseGate.LONG_MS); gate.held { long() } }` (job gardé dans un `remember`) ; tout `KeyUp` de sélection annule le job, puis `gate.onKey` comme aujourd'hui. Focus perdu : le job est annulé avec `gate.armed = false`. Un minuteur et pas les répétitions de touche : sans dépendre de ce que le Thor envoie pour A tenu.
  - `icon: ImageVector?` : `glyph` ne dessine rien si `icon` et `iconContent` sont null ; en `INLINE`, le `Spacer` de 6 dp suit le glyphe seulement s'il existe.
- [ ] **Step 5:** les tests de l'étape 1 et `IisuPanelTest` passent ; `./gradlew :app:assembleRelease` compile (les appelants actuels ne passent pas `onLongClick` : rien ne change pour eux).
- [ ] **Step 6:** Commit `Toggle tile: a long press, by touch or a held A`.

### Task 3: La fenêtre d'options, branchée dans les deux centres

**Files:**
- Create: `app/src/main/java/app/wayfinder/TileOptions.kt`
- Modify: `app/src/main/java/app/wayfinder/PanelShortcuts.kt:357-359` (`PanelTile`), l. 226-231 (Performance avec un tuner)
- Modify: `app/src/main/java/app/wayfinder/QuickPanelWindow.kt:96-100` (`PanelOwner` passe `internal`), l. 697-720 (`TilesGrid`)
- Modify: `app/src/main/java/app/wayfinder/NotificationCenter.kt` (racine l. 108-134, `Sheet` l. 202-226)

**Interfaces:**
- Consumes: `IisuToggleTile(…, onLongClick)` (tâche 2).
- Produces, dans `TileOptions.kt` :

```kotlin
sealed class TileOptions {
    /** One of [choices]: a pick runs [onPick] (its index), then the window closes. [footer]: a link or a switch under them. */
    class Choices(val choices: List<Choice>, val onPick: (Int) -> Unit,
                  val footer: (@Composable () -> Unit)? = null) : TileOptions()
    /** Its own content; the window stays open. */
    class Custom(val content: @Composable () -> Unit) : TileOptions()
}
class Choice(val label: String, val selected: Boolean)

/** The long press of panel tile [t]: its options (with a buzz), else its "why not" when [PanelTile.sayOnLong], else nothing. */
@Composable fun tileLongPress(t: PanelTile, open: () -> Unit): () -> Unit

/** [t]'s options window (t.options must not be null). */
@Composable fun TileOptionsSheet(t: PanelTile, onClose: () -> Unit)
```

- `PanelTile` gagne `val options: TileOptions? = null` et `val sayOnLong: Boolean = false` (« its action only says why it can't act: a long press says it too »). Seule Performance avec un tuner passe `sayOnLong = true` (le spec : toute autre tuile sans options reste muette, même grisée).

- [ ] **Step 1: `TileOptions.kt`.**
  - `tileLongPress` : `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` puis `open()` si `t.options != null` ; sinon `t.action()` si `t.sayOnLong` ; sinon rien.
  - `TileOptionsSheet` : la `GlassSheet` des contraintes globales, une `Column(Modifier.padding(18.dp).verticalScroll(…), spacedBy(10.dp))`, le `ModalHeader`. `Choices` : les choix `chunked(4)` en `Row(spacedBy(6.dp))` de `IisuToggleTile(null, label, selected, Modifier.weight(1f), height = 56.dp)`, cases vides en `Box(Modifier.weight(1f))` comme `TilesGrid` ; un clic : `onPick(i)` puis `onClose()`. Le `first` de la feuille est le `FocusRequester` du choix coché, sinon du premier. Puis `footer`. `Custom` : `content()`.
- [ ] **Step 2: Centre de contrôle.** `TilesGrid` garde les ids (`chosen` en paires id → tuile) et un `var optionsFor by remember { mutableStateOf<String?>(null) }`. Chaque tuile reçoit `onLongClick = tileLongPress(t) { optionsFor = id }`. Si `all[optionsFor]?.options != null`, `TileOptionsSheet(all.getValue(optionsFor), onClose = { optionsFor = null })` à la fin de `TilesGrid` (la `SheetHost` du `WindowRoot` la dessine par-dessus le panneau). L'état meurt avec la fenêtre du panneau : rien à remettre à zéro dans `startPanel`.
- [ ] **Step 3: Centre de notifications.**
  - La racine prend un `PanelOwner` (de `QuickPanelWindow.kt`, passé `internal`) à la place de `ComposeOwner`, et `root.setViewTreeOnBackPressedDispatcherOwner(owner)`.
  - B, Back : `if (owner.onBackPressedDispatcher.hasEnabledCallbacks()) owner.onBackPressedDispatcher.onBackPressed() else close()`, au relâchement comme aujourd'hui.
  - `setContent` enveloppe `Center(ctx)` dans `app.wayfinder.ui.WindowRoot { … }`.
  - `Sheet` : même `optionsFor` local et même branchement que l'étape 2 pour la rangée `TILES`. La `SheetHost` dessine la fenêtre sur tout le centre.
  - `remove()` n'a rien à remettre : l'état meurt avec la fenêtre.
- [ ] **Step 4:** `./gradlew :app:testDebugUnitTest :app:assembleRelease` passe.
- [ ] **Step 5: Sur le Thor.** Aucune tuile n'a encore d'options :
  - maintenir « Sleep », puis « Bottom screen » (doigt, puis A tenu) : rien ne se passe, pas de vibration ;
  - maintenir Battery saver en charge : rien non plus ;
  - avec ClusterTune ou Pulse installé, maintenir Performance : sa pastille.
- [ ] **Step 6: Sur le Thor, la rangée du centre de notifications.**
  - Glisser la rangée vers le haut : le centre se ferme.
  - Centre de contrôle et centre de notifications ouverts ensemble : rien ne fuit de l'un à l'autre.
  - Une fois la tâche 4 faite, refaire ces deux contrôles avec « Do not disturb ». Une fenêtre ouverte dans un centre :
    - n'apparaît pas dans l'autre ;
    - B la ferme, puis un second B ferme le centre.
- [ ] **Step 7:** Commit `Panel tiles: a long press opens their options window, in both centers`.

### Task 4: Les listes de choix (9 tuiles)

**Files:**
- Modify: `app/src/main/java/app/wayfinder/PanelShortcuts.kt` (`tiles()` : `perf`, `fan`, `lock`, `fps`, `boost`, `style`, `trigger`, `dnd`, `lightsonoff` ; `QuickSettings`)
- Create: `app/src/main/java/app/wayfinder/TileOptionRules.kt`
- Test: `app/src/test/java/app/wayfinder/TileOptionRulesTest.kt`

**Interfaces:**
- Consumes: `TileOptions.Choices`, `Choice`, `PanelTile.options` (tâche 3).
- Produces: `TileOptionRules.zenChoice(zenMode: Int): Int` ; `TileOptionRules.ZEN_CMD: List<String>` ; `TileOptionRules.nextLightProfile(cur: LightProfile, saved: LightProfile?, mode: LightMode): LightProfile` ; `QuickSettings.setPerformance(ctx, v: Int)`, `QuickSettings.setFan(v: Int)`, `QuickSettings.setStickLightMode(ctx, mode: LightMode)`.

- [ ] **Step 1: Écrire les tests**

```kotlin
class TileOptionRulesTest {
    // zen_mode: 0 off, 1 priority, 2 total silence, 3 alarms → the window's order: Off, Priority only, Alarms only, Total silence
    @Test fun zenChoice_mapsZenModeToTheWindowsOrder() {
        assertEquals(0, TileOptionRules.zenChoice(0)); assertEquals(1, TileOptionRules.zenChoice(1))
        assertEquals(2, TileOptionRules.zenChoice(3)); assertEquals(3, TileOptionRules.zenChoice(2))
        assertEquals(0, TileOptionRules.zenChoice(9))
    }
    @Test fun zenCmd_followsTheWindowsOrder() =
        assertEquals(listOf("off", "priority", "alarms", "none"), TileOptionRules.ZEN_CMD)
    @Test fun lightMode_fromOff_startsFromTheSavedProfile() {
        val saved = LightProfile(mode = LightMode.STATIC, left = 1, right = 2)
        assertEquals(saved.copy(mode = LightMode.SPECTRUM),
            TileOptionRules.nextLightProfile(LightProfile(mode = LightMode.OFF), saved, LightMode.SPECTRUM))
    }
    @Test fun lightMode_fromOffWithNothingSaved_startsFromTheDefault() =
        assertEquals(LightProfile().copy(mode = LightMode.BREATHING),
            TileOptionRules.nextLightProfile(LightProfile(mode = LightMode.OFF), null, LightMode.BREATHING))
    @Test fun lightMode_keepsColoursAndBrightness() {
        val cur = LightProfile(mode = LightMode.STATIC, left = 5, right = 6)
        assertEquals(cur.copy(mode = LightMode.STROBE), TileOptionRules.nextLightProfile(cur, null, LightMode.STROBE))
    }
}
```

- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "app.wayfinder.TileOptionRulesTest"` : échec.
- [ ] **Step 3:** `TileOptionRules` (objet pur). `QuickSettings` :
  - `setPerformance` fait l'écriture de `cyclePerformance` avec une valeur donnée, et `cyclePerformance` l'appelle ;
  - `setFan` reprend `PerfProfiles.markOwn` et l'écriture de `cycleFan`, et `cycleFan` l'appelle ;
  - `setStickLightMode` : vers Off, mémorise le profil (`lights_before_off`) comme `toggleStickLights` ; sinon `nextLightProfile` depuis le profil mémorisé, puis `LightSettings.setGlobalProfile` et `StickLights.apply`.
- [ ] **Step 4: Les options dans `tiles()`.** Chaque `onPick` fait ce que fait l'appui court pour cette valeur (`touch`, `refresh`), sans fermer le panneau sauf mention.

| id | Choix (mots exacts) | `onPick(i)` |
|---|---|---|
| `perf` (sans tuner) | `PerfMode.ayn` (« Standard », « Medium », « High ») | `touch("perf"); setPerformance(ctx, i); refresh(350); refresh(2000)` |
| `fan` | « Quiet », « Smart », « Sports », « Custom » | `touch("fan"); setFan(<QUIET, SMART, SPORTS, CUSTOM>[i].value); refresh(350)` ; footer : lien « Fan curve… » → `after(300) { QuickSettings.openFanCurve(ctx) }` |
| `lock` | « Off » (« Unlocked » si `focusSticky`), « Top screen », « Bottom screen » | `if (i == 0) setFocusLock(false) else setFocusLock(true, top = i == 1)` (Off garde l'écran mémorisé) ; footer : `GlassSwitchRow("Stays where you send it", focusSticky) { setFocusStickyOn(it); refresh(0) }` |
| `fps` | « Off » + `PanelRules.FPS_LEVELS` | `0` : `setFpsCounterOn(false); setFpsLevelTo(0)` ; sinon `setFpsLevelTo(i - 1); setFpsCounterOn(true)` ; puis `reapplyFps(); refresh(0)` et la pastille « The counter shows over games » comme la tuile |
| `boost` | « Off », « +6 dB », « +12 dB » | `SpeakerTune.setVolumeBoost(listOf(0, 6, 12)[i]); refresh(0)` |
| `style` | « Nintendo », « Xbox », « PlayStation » (aucun coché si `style == 2`) | `ButtonNames.setThorStyle(ctx, FaceStyle.values()[i]); refresh(500)` |
| `trigger` | « Analog », « Digital », « Both » | `QuickSettings.exec("settings put system trigger_input_mode $i"); refresh(500)` |
| `dnd` | « Off », « Priority only », « Alarms only », « Total silence » (coché : `zenChoice(glob("zen_mode", 0))`) | `QuickSettings.exec("cmd notification set_dnd ${ZEN_CMD[i]}"); refresh(500); refresh(1500)` |
| `lightsonoff` | `LightMode.values()` par leur `label` | `QuickSettings.setStickLightMode(ctx, LightMode.values()[i]); refresh(0)` ; footer : lien « Light settings… » → `open(HubPage.LIGHTS)` |

  Les liens du footer sont des `FocusableGlass` pleine largeur, comme « + Save what's on the screens now » de `PanelPairsDialog`.
- [ ] **Step 5:** Tests et `:app:assembleRelease` passent. Sur le Thor, chaque tuile au doigt puis à A tenu, dans le centre de contrôle (Do not disturb aussi dans le centre de notifications) :
  - le choix coché est la valeur actuelle ;
  - un choix l'applique, la fenêtre se ferme, la tuile affiche la nouvelle valeur ;
  - l'appui court passe toujours à la valeur suivante.

  Puis :
  - un choix de Performance, puis « Keep for <jeu> » apparaît ;
  - le profil d'un jeu change le ventilateur fenêtre ouverte : le choix coché suit.
- [ ] **Step 6:** Commit `Panel tiles: choose a value directly on a long press (9 tiles)`.

### Task 5: Les fenêtres sur mesure : Night light, Extra dim, Speaker sound fix

**Files:**
- Modify: `app/src/main/java/app/wayfinder/PanelShortcuts.kt` (`night`, `dim`, `speaker`, `speakereq`)
- Modify: `app/src/main/java/app/wayfinder/TileOptionRules.kt`, `app/src/test/java/app/wayfinder/TileOptionRulesTest.kt`

**Interfaces:**
- Consumes: `TileOptions.Custom` (tâche 3), `QuickSettings.rootInt`, `QuickSettings.exec`.
- Produces: `TileOptionRules.kelvinAt(fraction: Float, min: Int, max: Int): Int`, `TileOptionRules.fractionOf(kelvin: Int, min: Int, max: Int): Float`, `TileOptionRules.NIGHT_MIN = 2596`, `NIGHT_MAX = 4082`.

- [ ] **Step 1: Écrire les tests** — à droite (1f) le plus chaud, donc le moins de kelvins :

```kotlin
@Test fun nightLight_rightIsWarmer() {
    assertEquals(4082, TileOptionRules.kelvinAt(0f, 2596, 4082))
    assertEquals(2596, TileOptionRules.kelvinAt(1f, 2596, 4082))
    assertEquals(3339, TileOptionRules.kelvinAt(0.5f, 2596, 4082))
}
@Test fun nightLight_roundTrips() = assertEquals(0.25f, TileOptionRules.fractionOf(TileOptionRules.kelvinAt(0.25f, 2596, 4082), 2596, 4082), 0.01f)
@Test fun nightLight_clampsOutOfRange() {
    assertEquals(0f, TileOptionRules.fractionOf(9000, 2596, 4082), 0f); assertEquals(1f, TileOptionRules.fractionOf(1000, 2596, 4082), 0f)
}
```

- [ ] **Step 2:** échec, puis implémentation (arrondi `Math.round`), puis les tests passent.
- [ ] **Step 3: Les trois contenus `Custom`.** Un curseur est un `Text` (son nom) au-dessus d'un `GlassSlider(value, step = …) { … }`, comme `BigSlider` de `VolumeBar`. `GlassSlider` n'a que `onChange` : la valeur est gardée en état local et écrite 150 ms après le dernier mouvement (`LaunchedEffect(valeur) { delay(150); … }`), pas une commande root par pas.
  - `night` : `GlassSwitchRow("Night light", on)` (la commande de la tuile), puis le curseur « Intensity » sur `fractionOf(rootInt("secure", "night_display_color_temperature", NIGHT_MAX), NIGHT_MIN, NIGHT_MAX)`, écrit par `exec("settings put secure night_display_color_temperature ${kelvinAt(…)}")`. Les bornes : `NIGHT_MIN` / `NIGHT_MAX` (celles d'AOSP ; si le Thor en a d'autres, `Resources.getSystem().getIdentifier("config_nightDisplayColorTemperatureMin", "integer", "android")` les donne : les lire là d'abord).
  - `dim` : `GlassSwitchRow("Extra dim", on)`, puis un curseur sur `reduce_bright_colors_level` (0-100, `step = 0.05f`), écrit en `settings put secure`.
  - `speaker` et `speakereq` (la même fenêtre, titre « Speaker sound fix ») :
    - `GlassSwitchRow("Speaker sound fix", enabled) { setOn(it) }` ;
    - « Equalizer » (`setEq`, grisé et sans effet si la correction est éteinte) ;
    - une rangée de trois `IisuToggleTile(null, …)` « Off », « +6 dB », « +12 dB » (`setVolumeBoost`) ;
    - un curseur « Stereo width » de 1 à 3 (`setStereoWidth`, valeur `SpeakerTune.width`).
- [ ] **Step 4:** Sur le Thor : chaque réglage change l'écran ou le son comme dans les réglages d'Android ou la page Sound ; la fenêtre reste ouverte ; la tuile suit à la fermeture. Capture de chaque fenêtre en clair et en sombre.
- [ ] **Step 5:** Commit `Panel tiles: Night light, Extra dim and speaker options windows`.

### Task 6: Wi-Fi et Bluetooth

**Files:**
- Create: `app/src/main/java/app/wayfinder/RadioOptions.kt` (les deux contenus et leur lecture)
- Modify: `app/src/main/java/app/wayfinder/TileOptionRules.kt` (analyse des sorties de `cmd wifi`), `app/src/main/java/app/wayfinder/PanelShortcuts.kt` (`wifi`, `bt`), `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/app/wayfinder/TileOptionRulesTest.kt`, fixtures `app/src/test/resources/wifi/*.txt`

**Interfaces:**
- Consumes: `TileOptions.Custom` (tâche 3), `PServiceBridge.exec`, `QuickSettings.openSettings`.
- Produces: `TileOptionRules.currentSsid(status: String): String?` ; `TileOptionRules.savedInRange(networks: String, scan: String): List<String>` (les SSID enregistrés vus au dernier balayage, triés par signal) ; `@Composable fun WifiOptions(…)`, `@Composable fun BluetoothOptions(…)`.

- [ ] **Step 1: Sonder le Thor (il doit être branché).**
  - Enregistrer les sorties brutes, telles quelles, dans `app/src/test/resources/wifi/status.txt`, `networks.txt` et `scan.txt` : `adb shell cmd wifi status`, `adb shell cmd wifi list-networks`, `adb shell cmd wifi list-scan-results`.
  - Chercher une commande qui connecte un réseau enregistré sans mot de passe : `adb shell cmd wifi help | grep -i -E "connect|enable|select"`. L'essayer sur un réseau enregistré à portée.
  - Si elle marche, toucher un réseau l'utilise. Sinon, toucher un réseau ouvre le panneau Wi-Fi d'Android, `QuickSettings.openSettings(ctx, "android.settings.panel.action.WIFI")` sur l'écran du haut, qui connecte d'un toucher.
  - Bluetooth : vérifier que `pm grant app.wayfinder android.permission.BLUETOOTH_CONNECT` passe par le root, et que `BluetoothDevice.isConnected` répond par réflexion (`adb logcat` pendant un essai).
- [ ] **Step 2: Écrire les tests** sur les fixtures :
  - `currentSsid` rend le réseau connecté du Thor (le nom lu dans `status.txt`, sans guillemets), et null pour un texte « Wifi is disabled » ;
  - `savedInRange` rend les réseaux de `networks.txt` présents dans `scan.txt`, le plus fort d'abord, sans doublon (deux BSSID pour un même SSID).
- [ ] **Step 3:** échec, implémentation, les tests passent.
- [ ] **Step 4: `WifiOptions` et `BluetoothOptions`** (contenus `Custom`). Lecture hors du fil principal (`LaunchedEffect` + `Dispatchers.IO`) ; « Reading… » tant qu'elle n'a pas répondu.
  - **Wi-Fi :**
    - `GlassSwitchRow("Wi-Fi", on)` (la commande de la tuile, puis relecture 1,5 s après) ;
    - « Connected to <ssid> » ;
    - les réseaux de `savedInRange`, un `FocusableGlass` chacun (l'action de l'étape 1) ;
    - le lien « Wi-Fi settings… » → `after(250) { QuickSettings.openSettings(ctx, Settings.ACTION_WIFI_SETTINGS) }`.
  - **Bluetooth :**
    - `GlassSwitchRow("Bluetooth", on)` ;
    - les appareils de `BluetoothAdapter.bondedDevices` (permission déclarée dans le manifeste, accordée par `PServiceBridge.exec("pm grant …")` à la première ouverture si elle manque), les connectés en tête avec « Connected » ;
    - toucher un appareil → la page Bluetooth d'Android (`Settings.ACTION_BLUETOOTH_SETTINGS`) ;
    - lien « Bluetooth settings… ».
  - Wi-Fi ou Bluetooth éteint : la liste est remplacée par « Wi-Fi is off » ou « Bluetooth is off ».
- [ ] **Step 5:** Sur le Thor, dans les deux centres :
  - le réseau actuel et les réseaux à portée sont justes ;
  - toucher un réseau fait ce que l'étape 1 a établi ;
  - la manette ou le casque connecté est en tête ;
  - couper le Wi-Fi depuis la fenêtre met la tuile à jour.

  Captures.
- [ ] **Step 6:** Commit `Panel tiles: Wi-Fi and Bluetooth options windows`.

### Task 7: Passe finale et README

**Files:**
- Modify: `README.md` (liste « What changed » du fork)

- [ ] **Step 1:** README, liste « What changed » : une ligne « **Hold a tile** (control center, notification center): its options — Wi-Fi networks, Bluetooth devices, Do not disturb, Night light, Extra dim, speaker, lights — or pick a value directly (performance, fan, controller lock, frame rate, boost, face buttons, L2 / R2) » ; la ligne « Volume bar » ajoute « a touch outside closes it ».
- [ ] **Step 2:** Sur le Thor, la passe de la section Vérification du spec, en clair et en sombre : les 14 tuiles au doigt et à la manette ; l'appui court inchangé ; les tuiles C muettes à l'appui long ; la barre de son. Une capture par écran visité.
- [ ] **Step 3:** `./gradlew :app:testDebugUnitTest` : tous les tests passent. Commit `README: hold a tile for its options`.
