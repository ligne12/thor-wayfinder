# Barre de volume au style iiSU — plan de réalisation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Les touches de volume affichent une barre Wayfinder au style iiSU (mode sonnerie, jauge, « … ») à la place de celle d'Android.

**Architecture:** Le service d'accessibilité intercepte `KEYCODE_VOLUME_UP/DOWN`, règle le volume média sans l'interface d'Android et montre `VolumeBar`, une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` en Compose qui réutilise le thème et les cases du morceau 1. Les règles (pourcentage, icônes, mode suivant) sont des fonctions pures testées.

**Tech Stack:** Kotlin, Jetpack Compose, `AudioManager`, `WindowManager`, JUnit 4, adb (`sendevent`).

**Spec:** `docs/superpowers/specs/2026-11-12-barre-volume-iisu-design.md`

## Global Constraints

- Branche `iisu-look`, commits en anglais, sans URL de session, finis par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Fork privé : aucun `git push` vers `upstream`, aucune publication d'APK.
- Couleurs et arrondis uniquement par le thème (`LocalGlass`, `Radii`, `accentStops`) : aucune couleur écrite en dur dans `VolumeBar`.
- Fenêtre : `TYPE_ACCESSIBILITY_OVERLAY`, jamais focusable ; touchable, mais un toucher à côté va au jeu (`FLAG_NOT_TOUCH_MODAL`).
- Disparition 1,5 s après la dernière pression ou le dernier toucher, en fondu.
- Écran éteint ou appel en cours (`audio.mode != MODE_NORMAL`) : la touche n'est pas interceptée.
- Compilation et installation : `./gradlew :app:assembleRelease` puis `adb install -r app/build/outputs/apk/release/app-release.apk` (signée avec la clé de test, mise à jour en place).
- Sur le Thor : une action, une capture. Vérifier l'appli au premier plan avant tout appui simulé.

## Review Focus

1. Appui maintenu sur une touche de volume : le volume suit à chaque répétition, et la barre ne clignote pas (mise à jour, pas de recréation). Test : `direction_volumeKeysOnly` (tâche 1) ; absence de clignotement vérifiée sur le Thor (tâche 3, étape 1).
2. Barre ouverte sur un écran, puis manette changée d'écran : la barre suivante s'affiche sur le nouvel écran, l'ancienne se ferme. Vérification : tâche 3, étape 3.
3. Silencieux refusé sans accès « Ne pas déranger » : le bouton n'échoue pas, il saute le silencieux. Test : `nextRingerMode_withoutDnd_skipsSilent` (tâche 1).
4. Fenêtre impossible à ajouter : le volume change quand même, avec la barre d'Android. Vérification par lecture du code : tâche 2, étape 2.
5. Le « … » ouvre le panneau rapide sur l'écran de la barre, et la barre disparaît. Vérification : tâche 3, étape 2.

---

### Task 1: Règles de la barre (fonctions pures)

**Files:**
- Create: `app/src/main/java/app/wayfinder/VolumeRules.kt`
- Test: `app/src/test/java/app/wayfinder/VolumeRulesTest.kt`

**Interfaces:**
- Produces:

```kotlin
object VolumeRules {
    fun percent(level: Int, max: Int): Int                         // 0..100, max <= 0 -> 0
    enum class Speaker { OFF, LOW, HIGH }
    fun speaker(level: Int, max: Int): Speaker                      // 0 -> OFF, < 50 % -> LOW, else HIGH
    fun nextRingerMode(mode: Int, dndAccess: Boolean): Int          // AudioManager.RINGER_MODE_*
    fun direction(keyCode: Int): Int                                // VOLUME_UP +1, VOLUME_DOWN -1, else 0
}
```

- [ ] **Step 1: Écrire les tests**

```kotlin
@Test fun percent_roundsAndClamps() {
    assertEquals(0, VolumeRules.percent(0, 15)); assertEquals(53, VolumeRules.percent(8, 15)); assertEquals(100, VolumeRules.percent(15, 15))
    assertEquals(0, VolumeRules.percent(3, 0)); assertEquals(100, VolumeRules.percent(20, 15))
}
@Test fun speaker_offLowHigh() {
    assertEquals(VolumeRules.Speaker.OFF, VolumeRules.speaker(0, 15))
    assertEquals(VolumeRules.Speaker.LOW, VolumeRules.speaker(7, 15))
    assertEquals(VolumeRules.Speaker.HIGH, VolumeRules.speaker(8, 15))
}
@Test fun nextRingerMode_cyclesNormalVibrateSilent() {
    assertEquals(RINGER_MODE_VIBRATE, VolumeRules.nextRingerMode(RINGER_MODE_NORMAL, dndAccess = true))
    assertEquals(RINGER_MODE_SILENT, VolumeRules.nextRingerMode(RINGER_MODE_VIBRATE, dndAccess = true))
    assertEquals(RINGER_MODE_NORMAL, VolumeRules.nextRingerMode(RINGER_MODE_SILENT, dndAccess = true))
}
@Test fun nextRingerMode_withoutDnd_skipsSilent() {
    assertEquals(RINGER_MODE_NORMAL, VolumeRules.nextRingerMode(RINGER_MODE_VIBRATE, dndAccess = false))
}
@Test fun direction_volumeKeysOnly() {
    assertEquals(1, VolumeRules.direction(24)); assertEquals(-1, VolumeRules.direction(25)); assertEquals(0, VolumeRules.direction(96))
}
```

`RINGER_MODE_*` (0, 1, 2) et les codes de touche (24 Volume +, 25 Volume −, 96 bouton A) recopiés dans le test : le JVM des tests n'a pas `AudioManager` ni `KeyEvent`.

- [ ] **Step 2: Vérifier l'échec**

Run: `./gradlew :app:testDebugUnitTest --tests "app.wayfinder.VolumeRulesTest"`
Expected: échec de compilation, `VolumeRules` introuvable.

- [ ] **Step 3: Écrire `VolumeRules`** selon les signatures ci-dessus (constantes de mode : 0 silencieux, 1 vibreur, 2 sonnerie, comme `AudioManager`).

- [ ] **Step 4: Vérifier le succès**

Run: la même commande. Expected: 5 tests, 0 échec.

- [ ] **Step 5: Commit** — `git add` des deux fichiers ; message `Volume bar rules: percent, speaker icon, next ringer mode`.

### Task 2: La barre et l'interception des touches

**Files:**
- Create: `app/src/main/java/app/wayfinder/VolumeBar.kt`
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (`onKeyEvent`, vers la ligne 3510)
- Modify: `app/src/main/java/app/wayfinder/deck/VirtualInput.kt:63-64`

**Interfaces:**
- Consumes: `VolumeRules` (tâche 1) ; `ThorGlassTheme`, `GlassTokens.isDark`, `glassSurface`, `Radii.Card`, `accentStops` (morceau 1) ; `QuickPanelWindow.show(service, display)`.
- Produces:

```kotlin
object VolumeBar {
    /** Volume key handling for the accessibility service. True = consumed. */
    fun onVolumeKey(service: AccessibilityService, event: KeyEvent, display: Int): Boolean
    /** Adjusts media volume by [direction] (ADJUST_RAISE/LOWER) and shows / updates the bar on [display]. */
    fun adjust(ctx: Context, direction: Int, display: Int)
    fun close()
}
```

- [ ] **Step 1: `onVolumeKey`**
  - Ne fait rien et rend `false` si `VolumeRules.direction` vaut 0, si l'écran est éteint (`PowerManager.isInteractive`) ou si `audio.mode != MODE_NORMAL`.
  - Sur `ACTION_DOWN`, répétitions comprises : `adjust(...)`.
  - Rend `true` pour le DOWN comme pour le UP.
  - Branché dans `ForegroundAppService.onKeyEvent` juste après la capture du clavier Thor, avec `controllerDisplay()` comme écran.

- [ ] **Step 2: `adjust`**
  - `adjustStreamVolume(STREAM_MUSIC, direction, 0)` puis affichage ou mise à jour de la barre.
  - Si `wm.addView` échoue, nouveau réglage avec `FLAG_SHOW_UI` à la place : le volume ne doit jamais rester sans retour visible.
  - `VirtualInput.kt:63-64` appelle `VolumeBar.adjust` au lieu de son `adjustStreamVolume(..., FLAG_SHOW_UI)`.

- [ ] **Step 3: La fenêtre.** Calquée sur `QuickPanelWindow.show` :
  - `createDisplayContext`, `ComposeView` avec `ViewTreeLifecycleOwner` et `SavedStateRegistryOwner` comme le panneau ;
  - `WRAP_CONTENT`, `gravity = END or CENTER_VERTICAL`, `x = 16 dp` ;
  - drapeaux `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN`, titre `WayfinderVolumeBar`.

  Une seule fenêtre à la fois : sur un autre écran, l'ancienne est fermée avant d'ouvrir la nouvelle. Le niveau et le mode sont dans un `mutableStateOf`, la mise à jour ne recrée rien. Le minuteur de 1,5 s est relancé à chaque appel et à chaque toucher. La fermeture passe par un fondu `alpha` de 150 ms, puis `removeView`.

- [ ] **Step 4: Le contenu Compose**, dans `ThorGlassTheme(GlassTokens.isDark(ctx))` :
  - Colonne de 64 dp de large, `glassSurface(g, RoundedCornerShape(Radii.Card))`.
  - Bouton de mode, icônes `NotificationsActive` / `Vibration` / `NotificationsOff`. Le toucher appelle `nextRingerMode`, avec l'accès « Ne pas déranger » lu par `NotificationManager.isNotificationPolicyAccessGranted`.
  - Piste capsule de 12 × 160 dp, remplie depuis le bas avec `Brush.verticalGradient(g.accentStops.reversed())`. Le glissement vertical règle `setStreamVolume(STREAM_MUSIC, niveau, 0)`.
  - Icône du haut-parleur, pourcentage en `labelLarge`, puis `MoreHoriz` : son toucher appelle `close()` puis `QuickPanelWindow.show(service, display)`.

- [ ] **Step 5: Compiler, tester, installer**

Run: `./gradlew :app:testDebugUnitTest --tests "app.wayfinder.VolumeRulesTest" :app:assembleRelease` puis l'installation.
Expected: tests verts, `BUILD SUCCESSFUL`, `Success`.

- [ ] **Step 6: Commit** — message `Volume bar in iiSU's look replaces Android's on the volume keys`.

### Task 3: Vérification sur le Thor et réglages fins

**Files:** `VolumeBar.kt` seulement, pour les ajustements de taille ou de position.

- [ ] **Step 1: Touches réelles.** `adb shell sendevent /dev/input/event0 1 115 1; …0 0 0; …1 115 0; …0 0 0` (Volume +), puis 114 (Volume −). Capture de l'écran du jeu à chaque fois.
  Expected : la barre Wayfinder, pas celle d'Android ; le volume média change ; `settings get system secondary_screen_volume_level` suit ; la barre disparaît après 1,5 s.
- [ ] **Step 2: Touchers.** Bouton de mode (trois touchers : sonnerie, vibreur, silencieux, en lisant `dumpsys audio | grep -i ringer`), glissement sur la jauge, puis « … ». Expected : le panneau rapide s'ouvre sur cet écran.
- [ ] **Step 3: Écrans et thèmes.** Manette sur l'écran du bas, puis appui : la barre est en bas. Puis iiSU en sombre : la barre est sombre. Comparer côte à côte avec la capture d'Android (`Thor_20261112_152458_top.png`) et le panneau ; ajuster tailles et marges si Thomas le demande.
- [ ] **Step 4: Mode sonnerie remis** à sa valeur d'avant le test, volume remis.
- [ ] **Step 5: Commit** des ajustements éventuels — message `Volume bar: sizes checked on the Thor`.
