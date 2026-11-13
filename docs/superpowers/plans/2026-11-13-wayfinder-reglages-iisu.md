# Tout Wayfinder au style des réglages d'iiSU — Implementation Plan (part 6)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** every Wayfinder surface in iiSU's settings look — flat menu-card background, solid cards with their rim, a sober
focus tint instead of the halo — by changing the shared bricks, not each page.

**Architecture:** four shared places carry the look: `focusIndicator` (the focus), `glassSurface` + `GlassScreen` (cards and
backgrounds), `PageHeader` / the Hub's header (headers), and the two View-based overlays (`FocusCue`, `FpsOverlay`). Pages
follow without edits; the few that draw their own copy of a look are fixed where they copy it. Already in place, no task:
the section titles' hairline and the rows' "open" mark (a chevron in a disc, `GlassListRow`, part 1); the keyboard and the
Control Deck draw from the theme's tokens and follow.

**Tech Stack:** Kotlin, Jetpack Compose, Android Views (overlays), JUnit 4 (`./gradlew :app:testDebugUnitTest`).

**Spec:** `docs/superpowers/specs/2026-11-13-wayfinder-reglages-iisu-design.md`

## Global Constraints

- Private fork for one Thor: never publish, push only to `origin` (ligne12/thor-wayfinder), branch `iisu-look`.
- Chat in French; code, comments, commit messages in English; commits end with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and carry no session URL.
- On the Thor (adb 192.168.1.75:5555, `MSYS_NO_PATHCONV=1`): before EVERY action that opens, covers or closes anything,
  read `mCurrentFocus` on both screens and the recents list in the same command and stop if a game or an app not opened
  by us is there (Thomas tests on the device at the same time). One action, then a capture. Never touch GameNative.
- Install = `./gradlew :app:assembleRelease` then `adb install -r` (restarts Wayfinder: only when no game is open).
- Colours: menu card `menuCard(g)` (`#F1F6F8` light, `g.surfaceModal` dark = `#2E2F34`); cards `g.surfaceModal`
  (white light); iiSU's focused row `#E2E5E7` on a `#F5F7F8` card (light). The "on" gradient (`selectedFill`, toggle
  tiles) stays.
- The control center must still fit without scrolling with the default cards (Levels spacing measured in f99c0cf).

## Review Focus

1. A focused element that is "on" (gradient: Fan, Controller lock, a selected option) or a picture (a multitask card): the
   person must still see which element the controller is on.
2. The focus arriving below the fold of a long page (Remap, Hints, More): the element still scrolls into view with its
   24 dp margin — the bring-into-view part of `focusIndicator` is kept, only its drawing changes.
3. Inner controls (a slider in a row, a text field, the EQ curve, the colour wheel): exactly one tint shows — the slider's
   row, not the slider and its row both, and not the row when its chevron button has the focus.
4. The Hub opened over a game, the companion screen, the control center: opaque, and no window still asks Android for a blur
   it no longer shows (GPU).
5. A theme switch (light/dark, iiSU's own) while a page or the control center is open: tints and cards follow (7344631).

---

### Task 1: The sober focus

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ui/GlassComponents.kt` (`focusIndicator`, its constants, `FocusableGlass` if it
  scales or rings on its own)
- Modify: `app/src/main/java/app/wayfinder/ui/ColorControls.kt` (`LocalSliderRow`, `SliderRow`, `GlassSlider`)
- Test: `app/src/test/java/app/wayfinder/ui/IisuPanelTest.kt`

**Interfaces:**
- Produces: `internal fun focusTint(dark: Boolean): Color` in `ui/GlassComponents.kt`; `Modifier.focusIndicator(shape, inset,
  tint: Boolean = true)` — same bring-into-view as today, draws `focusTint(g.dark)` over the element (inside [shape]) while
  it has the focus, fading in with the existing `halo` spring; no ring, no scale, no turning animation.
- Produces: `LocalSliderRow: CompositionLocal<MutableState<Boolean>?>` (null outside a `SliderRow`): the row draws the tint
  over its own bounds (`RoundedCornerShape(Radii.Control)`) while its slider has the focus; the slider inside a row passes
  `tint = false`.

- [ ] **Step 1: Measure iiSU's dark focus.** Ask Thomas to open iiSU › Settings in the dark theme with a row focused by
  the controller and say when; then a read-only `screencap` of the top screen, and read the focused row's colour against
  its card (as `#E2E5E7` on `#F5F7F8` in light). No capture possible: ledger a Ruling and use white at the light alpha.

- [ ] **Step 2: Write the failing test** in `IisuPanelTest.kt`:

```kotlin
@Test fun focusTint_matchesIisusFocusedRow() {
    fun close(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue)) <= 2f / 255f
    assertTrue(close(focusTint(dark = false).compositeOver(Color(0xFFF5F7F8)), Color(0xFFE2E5E7)))
    assertTrue(close(focusTint(dark = true).compositeOver(Color(0xFF2E2F34)), Color(0xFF______)))   // Step 1's measure
}
```

- [ ] **Step 3: Run it** — `./gradlew :app:testDebugUnitTest --tests 'app.wayfinder.ui.IisuPanelTest'`.
  Expected: FAIL, `focusTint` unresolved.
- [ ] **Step 4: Implement** `focusTint` (black over light, white over dark, alphas that pass the test), the new drawing in
  `focusIndicator` (`drawWithContent { drawContent(); drawOutline(outline, focusTint(g.dark), alpha = halo) }`), and the
  slider row's tint. Delete what only the ring used: `RING_TURN_MS`, `FOCUS_SCALE`, `FOCUS_GROW_MAX`, the `turn`
  transition, the `grow` spring and its `graphicsLayer`.
- [ ] **Step 5: Run the whole suite** — `bash .superpowers/sdd/*/tests.sh` (or `./gradlew :app:testDebugUnitTest`).
  Expected: all pass (39).
- [ ] **Step 6: On the Thor**, release install, then captures with the controller's D-pad: the control center (a plain
  tile, an "on" tile, the Brightness row, its chevron), the notification center's tiles, a multitask card; light and dark.
  Expected: one tint, on the element that has the focus; Review Focus 1 and 3 hold.
- [ ] **Step 7: Commit** — `Focus: iiSU's sober tint everywhere instead of the halo`.

### Task 2: Flat backgrounds, solid cards

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ui/GlassComponents.kt` (`GlassScreen`, `glassSurface`, `GlassPanel`,
  `FocusableGlass`, `LocalRealGlass`, the aurora / dots / `AuroraSpan` code)
- Modify: callers that pass the removed parameters (`raised =`, `veil =`, `span =`): `AppProfilesScreen.kt`,
  `DeckEditor.kt`, `Hub.kt`, `NotificationCenter.kt`, `QuickPanelWindow.kt`, `RecentsScreen.kt`, `ShortcutGridEditor.kt`,
  `ui/ColorControls.kt`, `ui/IisuPanel.kt`, `VolumeBar.kt`, `Tutorial.kt`, `CompanionActivity.kt`,
  `CompanionPresentation.kt`, `MainActivity.kt` (the compiler lists them)
- Modify: `MainActivity.kt` (`realGlass`, `applyBackdrop`), `QuickPanelWindow.kt` (`blur`, `FLAG_BLUR_BEHIND`),
  `CompanionPresentation.kt` (`applyBackdrop`), `Hub.kt` (`AppearancePage`, `appearanceSummary`), `SearchIndex.kt` /
  `NavPaths.kt` (the removed cards' entries)

**Interfaces:**
- Produces: `fun Modifier.glassSurface(g: GlassColors, shape: Shape, well: Color = Color.Unspecified): Modifier` — an
  unspecified [well] is `g.surfaceModal`; keeps the short shadow, the inner glow, `glassLight` and the 1 dp rim; no veil,
  no lens. `GlassPanel(modifier, radius, content)` loses `strong`.
- Produces: `GlassScreen(modifier, content)` — `menuCard(g)` behind its content, the `LocalGlassBackdrop` /
  `LocalFocusRole` providers kept if still read; no `span`.

- [ ] **Step 1: Change `glassSurface` and `GlassScreen`** as above; delete the aurora (`BLOBS`, `Blob`, `drawAurora`,
  `realTint`, `iisuBackdrop`, `iisuDotPaint`), `AuroraSpan` / `spanFor`, and `LocalRealGlass` with its three providers.
- [ ] **Step 2: Build** — `./gradlew :app:assembleRelease -q`. Expected: errors only at callers of removed parameters;
  fix each by dropping the argument (a `well =` stays).
- [ ] **Step 3: Remove the windows' blur**: `MainActivity.applyBackdrop` and its callers, `CompanionPresentation.applyBackdrop`,
  the quick panel's `blur` (its `FLAG_BLUR_BEHIND`, `blurBehindRadius`). The keyboard's and the deck's blur stay (not in
  the spec).
- [ ] **Step 4: Appearance**: remove the BACKDROP, SEE_THROUGH and REFRACTION cards from `AppearancePage`'s `MoreOptions`
  and their search entries; `appearanceSummary()` returns `"Follows iiSU"`. Settings fields in `AppSettings` stay (backup
  compatibility).
- [ ] **Step 5: Build and run the suite.** Expected: build OK, all tests pass.
- [ ] **Step 6: On the Thor**, captures light and dark: the Hub's home, Screens, Remap (a game's controls), a sheet (Games ›
  Import), the companion screen if shown, the control center. Expected: flat backgrounds, solid cards with their rim, no
  aurora, no dots; the control center unchanged in size.
- [ ] **Step 7: Commit** — `Flat backgrounds and solid cards everywhere, no window blur left`.

### Task 3: Headers

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ui/IisuPanel.kt` (new `IisuRoundButton`)
- Modify: `app/src/main/java/app/wayfinder/QuickPanelWindow.kt` (its private `RoundButton` → `IisuRoundButton`)
- Modify: `app/src/main/java/app/wayfinder/ui/GlassControls.kt` (`PageHeader`)
- Modify: `app/src/main/java/app/wayfinder/Hub.kt` (`HubHome`'s Ko-fi button, `StatusStrip`)

**Interfaces:**
- Produces: `@Composable fun IisuRoundButton(icon: ImageVector, description: String, onClick: () -> Unit,
  focusRequester: FocusRequester? = null, size: Dp = 36.dp)` — the control center's round button (a `FocusableGlass`
  circle, a 20 dp icon in `g.textPrimary`, `contentDescription` = [description]).
- Consumes: `BackPillDefaults.HEIGHT` (44 dp) for the page's round Back, so `PageHeader`'s alignment math is unchanged.

- [ ] **Step 1: Move** the quick panel's `RoundButton` to `IisuRoundButton` in `IisuPanel.kt`; the panel calls it.
- [ ] **Step 2: `PageHeader`**: its `BackPill` becomes `IisuRoundButton(ArrowBack, backLabel, onBack, size =
  BackPillDefaults.HEIGHT)` wrapped in `CompositionLocalProvider(LocalEntryFocus provides null)` (never a page's entry, as
  the pill). `BackPill` itself stays for `ModalHeader` and `RemapScreen`'s measured header.
- [ ] **Step 3: Hub home**: Ko-fi → `IisuRoundButton(Coffee, "Support me on Ko-fi", …)` keeping its `focusProperties`;
  `StatusStrip`'s `GlassPanel` → a plain `Row` with the same cells and padding (no card).
- [ ] **Step 4: Build and run the suite.** Expected: OK.
- [ ] **Step 5: On the Thor**, captures: the Hub's home (header, strip), Screens' header; B and the round Back both go back;
  Up from the first tile row still reaches Search; Left from Search reaches Ko-fi.
- [ ] **Step 6: Commit** — `Headers: a round Back on pages, Ko-fi round, the status strip without a card`.

### Task 4: Over the games

**Files:**
- Modify: `app/src/main/java/app/wayfinder/FocusCue.kt` (the cue's background and font)
- Modify: `app/src/main/java/app/wayfinder/FpsOverlay.kt` (`PILL`, text colour, font)
- Modify: `app/src/main/java/app/wayfinder/Hub.kt` (`FpsPreview`: the same colours as the overlay)

**Interfaces:**
- Produces: in `FpsOverlay`, `fun pillColors(ctx: Context): Pair<Int, Int>` (background, text) from `GlassTokens.resolve(ctx)`
  — `menuCard(g)` and `g.textPrimary`; `FpsPreview` reads it instead of `PILL`.

- [ ] **Step 1: FocusCue**: every cue on `menuCard(g)`, opaque (drop the 95 % glass and the `frost` layer), the
  `RimDrawable` kept; text in Cal Sans (`ResourcesCompat.getFont(ctx, R.font.cal_sans)`, the system font if it can't load).
- [ ] **Step 2: FpsOverlay and FpsPreview**: `pillColors`, Cal Sans, corners unchanged.
- [ ] **Step 3: Build and run the suite.** Expected: OK.
- [ ] **Step 4: On the Thor**, captures: a pill (Screenshot from the control center: "Screenshot saved"), a held list (Home
  held on the controller), the FPS counter (control center › Frame rate), light and dark. Then the counter back off.
- [ ] **Step 5: Commit** — `Cues and the FPS counter on iiSU's menu card`.
