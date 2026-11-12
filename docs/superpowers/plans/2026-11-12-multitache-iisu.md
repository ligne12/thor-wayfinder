# Multitâche au style iiSU — plan de réalisation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quand l'écran Récents d'Android s'ouvre, Wayfinder le recouvre de son propre multitâche au style iiSU (carrousel de cartes, écran 1/2, cadenas, capture, tout effacer), au toucher et à la manette.

**Architecture:** Un mode « list » de l'outil root existant `RecentsTool` écrit la liste des tâches et copie leurs aperçus dans le cache de Wayfinder. `RecentsScreen`, une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` plein écran en Compose, s'affiche à l'événement de fenêtre de Récents et agit par les outils root déjà là (`frontTask`, `moveTaskViaBinder`, `goHomeOnDisplay`, le mode « effacer » de `RecentsTool`). Les règles (lecture de la liste, ordre, carte de départ, ce que « Tout effacer » garde) sont pures et testées.

**Tech Stack:** Kotlin, Jetpack Compose, `IActivityTaskManager` par réflexion dans `app_process` (root via pservice), `WindowManager`, `MediaStore`, JUnit 4, adb (`input motionevent`, `screenrecord`, `run-as`).

**Spec:** `docs/superpowers/specs/2026-11-12-multitache-iisu-design.md`

## Global Constraints

- Branche `iisu-look`, commits en anglais, sans URL de session, finis par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Fork privé : aucun `git push` vers `upstream`, aucune publication d'APK. Aucune nouvelle dépendance.
- Couleurs, arrondis et police uniquement par le thème (`LocalGlass`, `Radii`, `IisuLookSource`) : rien d'écrit en dur dans `RecentsScreen`.
- Fenêtre : `TYPE_ACCESSIBILITY_OVERLAY`, plein écran, focusable (la bande de sélection d'iiSU ne tourne que dans une fenêtre qui a le focus, et le retour d'Android arrive à la fenêtre focusée), titre `WayfinderRecents`, fondu de 150 ms à l'entrée et à la sortie.
- Aucun appel root (`PServiceBridge`) sur le fil principal.
- Si la liste ne peut pas être lue ou la fenêtre pas ajoutée : pas de recouvrement, l'écran Récents d'Android reste avec la manette de Wayfinder comme avant.
- Compilation : `./gradlew :app:assembleRelease` (version finale) ou `:app:assembleDebug` (journaux `Log.d`, `run-as`) ; même clé, `adb install -r`. Remettre la version finale à la fin de chaque passage sur le Thor.
- Sur le Thor : une action, une capture ; vérifier l'appli au premier plan avant tout geste ; ne jamais toucher GameNative ; ne jamais faire de geste de bord sur les réglages d'iiSU. L'appli de test est Paramètres d'Android (`am start --display 0 -a android.settings.SETTINGS`), dont la tâche est fermée à la fin (`am stack remove <tâche>`).
- Geste qui ouvre Récents : `input -d 0 motionevent DOWN 960 1079`, puis `MOVE 960 1000`, `MOVE 960 850`, `MOVE 960 700`, une pause de 0,8 s, `MOVE 960 699`, `UP 960 699`. `input swipe` file vers l'accueil.

## Review Focus

1. Un balayage qui finit à l'accueil : le multitâche s'efface et ne reste pas posé sur l'accueil. Vérification : tâche 3, étape 6.
2. L'outil root lent ou en échec (premier lancement, pservice absent) : pas d'écran vide posé sur Récents. Vérification : tâche 3, étape 6 (première ouverture après installation), et lecture du code de `RecentsScreen.show`.
3. La dernière carte fermée, ou celle de l'appli quittée : retour à l'accueil de cet écran, pas un écran vide. Vérification : tâche 3, étape 7.
4. Une appli verrouillée et l'action « Fermer les applis » de Wayfinder : elle reste. Test : `clearKeep_keepsLockedOtherScreenAndAlways` (tâche 2) ; branchement vérifié tâche 2, étape 4.
5. Beaucoup de tâches (dix et plus) : aperçus décodés en basse résolution, carrousel paresseux, pas de saccade au défilement. Vérification : tâche 5, étape 3.

---

### Task 1: Liste des tâches et aperçus (outil root)

**Files:**
- Create: `app/src/main/java/app/wayfinder/RecentsModel.kt`
- Create: `app/src/main/java/app/wayfinder/RecentsSource.kt`
- Modify: `app/src/main/java/app/wayfinder/RecentsTool.kt` (un mode `list` et un mode `shot` ; le mode « effacer » actuel ne change pas)
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (à l'événement de fenêtre de Récents, `RecentsSource.refreshAsync`)
- Test: `app/src/test/java/app/wayfinder/RecentsModelTest.kt`

**Interfaces:**
- Produces:
  - `data class RecentTask(val taskId: Int, val pkg: String, val display: Int, val lastActive: Long)`
  - `object RecentsModel { fun parse(lines: List<String>): List<RecentTask> }`
  - `object RecentsSource { var tasks: List<RecentTask>? (état Compose, null = jamais lue) ; fun refreshAsync(ctx: Context, done: (Boolean) -> Unit = {}) ; fun shotFile(ctx: Context, taskId: Int): File? ; fun saveScreenshot(ctx: Context, task: RecentTask): Boolean }`
  - `RecentsTool list <dir>` écrit `<dir>/tasks.txt` et `<dir>/<taskId>.jpg`, imprime `OK tasks=N shots=M` ; `RecentsTool shot <taskId> <dir>` copie l'aperçu pleine résolution en `<dir>/shot.jpg`, imprime `OK` ou `ERR …`.

- [ ] **Step 1: Write the failing test**

```kotlin
class RecentsModelTest {
    @Test fun parse_keepsStandardAndUnknownTasks() {
        val t = RecentsModel.parse(listOf(
            "1201\tcom.android.chrome\t4\t971314107\t1",
            "1174\tcom.iisulauncher\t0\t971314107\t2",      // home
            "1157\tcom.android.launcher3\t0\t1\t3",          // Recents itself
            "13\tapp.x\t0\t5\t-1",                           // type unread: kept
            "garbage", "12\tbad pkg!\t0\t5\t1", "x\tapp.y\t0\t5\t1"))
        assertEquals(listOf(RecentTask(1201, "com.android.chrome", 4, 971314107), RecentTask(13, "app.x", 0, 5)), t)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests app.wayfinder.RecentsModelTest`
Expected: FAIL, `Unresolved reference: RecentsModel`.

- [ ] **Step 3: Implement `RecentTask` and `RecentsModel.parse` in `RecentsModel.kt`**

Une ligne = `taskId\tpkg\tdisplay\tlastActive\ttype`, séparée par des tabulations. Gardée si les cinq champs se lisent, si le paquet correspond à `[A-Za-z0-9_.]+`, et si le type vaut 1 (standard) ou -1 (non lu). Les types 2 (accueil) et 3 (Récents) sont écartés.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests app.wayfinder.RecentsModelTest`
Expected: PASS (1 test).

- [ ] **Step 5: Add the `list` and `shot` modes to `RecentsTool`**

`main(args)` : `args[0] == "list"` → `list(File(args[1]))` ; `args[0] == "shot"` → `shot(args[1].toInt(), File(args[2]))` ; sinon le mode « effacer » actuel, inchangé.
- `list` : `getRecentTasks` comme le mode « effacer ». Pour chaque `RecentTaskInfo` avec `taskId >= 0`, une ligne `taskId, paquet (même ordre de repli que le mode « effacer »), displayId, lastActiveTime, topActivityType`. `displayId` et `topActivityType` sont des champs cachés de `TaskInfo`, lus comme `RootMover.displayOf`, avec -1 si illisibles. La liste s'écrit dans `tasks.txt` (via un fichier temporaire puis renommage). Pour chaque tâche, la première image lisible parmi `/data/system_ce/0/snapshots/<id>_reduced.jpg` et `<id>.jpg` est copiée en `<dir>/<id>.jpg`. Chaque fichier écrit passe en `setReadable(true, false)`. Les aperçus de tâches disparues sont effacés. Sortie : `OK tasks=N shots=M snapdir=<oui|non>`.
- `shot` : copie `/data/system_ce/0/snapshots/<id>.jpg` (repli : `_reduced`) en `<dir>/shot.jpg`, lisible par tous.

- [ ] **Step 6: Implement `RecentsSource`**

- Dossier : `File(ctx.cacheDir, "recents")`, créé par l'appli avant l'appel.
- `refreshAsync` : sur un fil à part, un appel à la fois (`AtomicBoolean`). Il lance `PServiceBridge.runEntryPoint(ctx, "RecentsTool", "list", dir.absolutePath)`. Sur une sortie qui commence par `OK`, il lit `tasks.txt` avec `RecentsModel.parse`, met `tasks` à jour sur le fil principal et appelle `done(true)` ; sinon `done(false)`. Une ligne `Log.w` en cas d'échec.
- `shotFile` : `File(dir, "$taskId.jpg")`, si le fichier se lit.
- `saveScreenshot` : sur le fil de l'appelant (jamais le principal). Il lance le mode `shot`, puis insère `shot.jpg` dans `MediaStore.Images` : `RELATIVE_PATH` `Pictures/Screenshots`, nom `Screenshot_<yyyyMMdd-HHmmss>_<pkg>.jpg`, type `image/jpeg`. Retour : succès.

Dans `ForegroundAppService`, là où l'événement de fenêtre de Récents est reconnu (`if (recents)`, avant `enterRecents`) : `RecentsSource.refreshAsync(this) { ok -> Log.d(TAG, "recents list: $ok, ${RecentsSource.tasks?.size} tasks") }`.

- [ ] **Step 7: Probe on the Thor (debug build)**

Run :
- `./gradlew :app:assembleDebug`, `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Ouvrir Paramètres sur l'écran du haut, `logcat -c`, faire le geste des contraintes, puis lire `logcat -d | grep -E "recents list|RecentsTool"`.
- Lire `adb shell run-as app.wayfinder ls -l cache/recents` et `adb shell run-as app.wayfinder cat cache/recents/tasks.txt`.
- Revenir à l'accueil (`input -d 0 keyevent KEYCODE_HOME`) et fermer la tâche Paramètres.

Expected :
- `recents list: true, N tasks` avec N ≥ 1 ;
- `tasks.txt` liste Paramètres avec l'écran 0 ;
- au moins un `<id>.jpg`, lisible par `run-as` ;
- noter le temps entre l'événement et la ligne du journal (le coût d'un lancement de l'outil).

Si `snapdir=non` ou si aucun aperçu ne se lit, c'est un défaut du plan : décider dans le journal (`Ruling:`) entre `getTaskSnapshot` dans l'outil et le dossier `Android/data/app.wayfinder/files`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/wayfinder/RecentsModel.kt app/src/main/java/app/wayfinder/RecentsSource.kt app/src/main/java/app/wayfinder/RecentsTool.kt app/src/main/java/app/wayfinder/ForegroundAppService.kt app/src/test/java/app/wayfinder/RecentsModelTest.kt
git commit -m "Recents list and snapshots through the root tool"
```

---

### Task 2: Règles du carrousel et cadenas

**Files:**
- Modify: `app/src/main/java/app/wayfinder/RecentsModel.kt`
- Modify: `app/src/main/java/app/wayfinder/AppSettings.kt` (les verrous)
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (`clearBackgroundApps` garde les verrouillées)
- Test: `app/src/test/java/app/wayfinder/RecentsModelTest.kt`

**Interfaces:**
- Consumes: `RecentTask` (tâche 1).
- Produces:
  - `RecentsModel.order(tasks: List<RecentTask>): List<RecentTask>` : les plus anciennes d'abord (à gauche) ;
  - `RecentsModel.initial(ordered: List<RecentTask>, origin: String?): Int` : l'index de départ, -1 si la liste est vide ;
  - `RecentsModel.clearKeep(locked: Set<String>, otherScreenApp: String?, always: Set<String>): Set<String>` ;
  - `AppSettings.recentsLocked: Set<String>` (état Compose, préférence `recents_locked`) et `AppSettings.toggleRecentsLock(pkg: String)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
    private fun t(id: Int, pkg: String, at: Long, d: Int = 0) = RecentTask(id, pkg, d, at)

    @Test fun order_oldestFirst_tieByTaskId() {
        assertEquals(listOf(3, 1, 2), RecentsModel.order(listOf(t(1, "a", 20), t(2, "b", 30), t(3, "c", 10))).map { it.taskId })
        assertEquals(listOf(1, 2), RecentsModel.order(listOf(t(2, "b", 5), t(1, "a", 5))).map { it.taskId })
    }

    @Test fun initial_originElseNewest() {
        val o = listOf(t(1, "a", 1), t(2, "b", 2), t(3, "a", 3), t(4, "c", 4))
        assertEquals(2, RecentsModel.initial(o, "a"))      // the newest task of the app left
        assertEquals(3, RecentsModel.initial(o, null))     // from home: the newest
        assertEquals(3, RecentsModel.initial(o, "zz"))     // not listed: the newest
        assertEquals(-1, RecentsModel.initial(emptyList(), "a"))
    }

    @Test fun clearKeep_keepsLockedOtherScreenAndAlways() {
        assertEquals(setOf("lock.a", "other.b", "home.c", "app.wayfinder"),
            RecentsModel.clearKeep(setOf("lock.a"), "other.b", setOf("home.c", "app.wayfinder")))
        assertEquals(setOf("home.c"), RecentsModel.clearKeep(emptySet(), null, setOf("home.c")))
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests app.wayfinder.RecentsModelTest`
Expected: FAIL, `Unresolved reference: order` (and `initial`, `clearKeep`).

- [ ] **Step 3: Implement `order`, `initial`, `clearKeep`**

`order` : tri par `lastActive` croissant, puis `taskId` croissant. `initial` : dernier index dont le paquet vaut `origin`, sinon `lastIndex`. `clearKeep` : l'union des trois.

- [ ] **Step 4: Locks in `AppSettings`, honoured by "Close apps"**

- `recentsLocked`, chargé avec les autres préférences (`getStringSet("recents_locked")`, copie défensive).
- `toggleRecentsLock(pkg)` ajoute ou retire le paquet et enregistre.
- Dans `ForegroundAppService.clearBackgroundApps`, l'ensemble `keep` reçoit aussi `addAll(AppSettings.recentsLocked)`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (RecentsModelTest 4, VolumeRulesTest 10, IisuLookTest 16).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/wayfinder/RecentsModel.kt app/src/main/java/app/wayfinder/AppSettings.kt app/src/main/java/app/wayfinder/ForegroundAppService.kt app/src/test/java/app/wayfinder/RecentsModelTest.kt
git commit -m "Recents rules: card order, first card, what Clear all keeps; locks"
```

---

### Task 3: L'écran multitâche, au toucher

**Files:**
- Create: `app/src/main/java/app/wayfinder/RecentsScreen.kt`
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (afficher à l'événement de Récents, fermer à sa sortie)

**Interfaces:**
- Consumes: `RecentsSource`, `RecentsModel` (tâches 1-2), `PServiceBridge.frontTask`, `moveTaskViaBinder`, `goHomeOnDisplay`, `runEntryPoint(ctx, "RecentsTool", keepCsv)`, `Overlays.pill`, `GlassScreen`, `GlassButton`, `focusIndicator`, `glassSurface`.
- Produces:
  - `object RecentsScreen`, toutes ses fonctions sur le fil principal :
    - `fun show(service: AccessibilityService, display: Int, origin: String?, always: Set<String>, otherScreenApp: () -> String?): Boolean`
    - `fun close()`
    - `val shownOn: Int?`
    - `fun browse(dir: Int)`, `fun openSelected()`, `fun closeSelected()`, `fun clearAll()`, `fun back(): Boolean` (false : rien à rouvrir, l'appelant fait son retour habituel)
  - `always` : les accueils et Wayfinder (`launcherPackages + packageName`) ; `otherScreenApp` : `displayApps` de l'autre écran.

- [ ] **Step 1: The window**

Un seul multitâche à la fois : déjà montré sur `display`, `show` ne fait rien ; montré sur l'autre écran, `close()` d'abord. La fenêtre montre `RecentsSource.tasks` et suit ses mises à jour (état Compose).
- Construction comme `QuickPanelWindow.show` : contexte d'écran, `ContextThemeWrapper(..., R.style.Theme_Wayfinder_Glass)`, `ComposeOwner`, protection par `onViewDetachedFromWindow`.
- Paramètres de fenêtre : `MATCH_PARENT` × `MATCH_PARENT`, `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED`, focusable, titre `WayfinderRecents`.
- Fondu : `alpha` 0 → 1 en 150 ms à l'entrée ; `close()` après un fondu de 150 ms qui ne ferme que sa propre vue (`if (view === v)`).
- `dispatchKeyEvent` de la vue racine : `KEYCODE_BACK` en relâché → `back()`, ou à défaut `GLOBAL_ACTION_BACK`.
- `IisuLookSource.refreshAsync(service)` à l'ouverture.

- [ ] **Step 2: The carousel**

- Contenu : `ThorGlassTheme(dark = GlassTokens.isDark(ctx)) { CappedFontScale { GlassScreen { … } } }`.
- Cartes : `RecentsModel.order(tasks)` dans une `LazyRow` centrée (marges latérales pour que la première et la dernière carte puissent être au centre), espacement 24 dp. L'index de départ vient de `RecentsModel.initial(…, origin)`.
- Une carte : 60 % de la hauteur de l'écran ; largeur selon le format de l'aperçu (16:9 sans aperçu) ; coins `Radii.Card`.
  - Au-dessus : icône 28 dp (`getApplicationIcon(pkg).toBitmap(96, 96)`), nom de l'appli en `titleSmall`, badge `1` (écran 0) ou `2`, cadenas `Lock` / `LockOpen`.
  - Dessous : l'aperçu (`BitmapFactory.decodeFile` hors du fil principal, `inSampleSize` 2 s'il dépasse 960 px de large), `ContentScale.Crop` ; à défaut, l'icône en 96 dp sur `glassSurface`.
- Carte choisie : `focusIndicator(RoundedCornerShape(Radii.Card))` et un `FocusRequester` appelé quand le choix change ; elle est amenée au centre (`animateScrollToItem`).
- En bas, centrés : `GlassButton("Screenshot")` et `GlassButton("Clear all")`.

- [ ] **Step 3: The actions**

Chaque action root passe par un fil à part, puis revient au fil principal.
- **Toucher une carte** : la choisir et l'ouvrir. `openSelected` → `frontTask(ctx, pkg, display)`, puis `close()`.
- **Glisser une carte vers le haut** (`detectVerticalDragGestures`) : au-delà de 25 % de sa hauteur, elle part vers le haut et `closeSelected` s'exécute. `closeSelected` → `PServiceBridge.exec("am stack remove $taskId")`, puis retrait de la liste. Si la liste est vide : `goHomeOnDisplay(display)` et `close()`.
- **Badge** → `moveTaskViaBinder(ctx, pkg, autre écran, display)`, puis `RecentsSource.refreshAsync`.
- **Cadenas** → `AppSettings.toggleRecentsLock(pkg)`.
- **Screenshot** → `RecentsSource.saveScreenshot` sur la carte choisie, puis `Overlays.pill("Screenshot saved" | "No screenshot for this app", CueOrigin.Screen(display))`.
- **Clear all** → `runEntryPoint(ctx, "RecentsTool", RecentsModel.clearKeep(AppSettings.recentsLocked, otherScreenApp(), always).joinToString(","))`, puis `goHomeOnDisplay(display)` et `close()`.
- **Toucher hors des cartes et des boutons** → `back()`.
- `back()` : si `origin` est une carte de la liste, l'ouvrir et renvoyer true ; sinon false.
- `browse(dir)` : choisir la carte voisine, bornée aux extrémités.

- [ ] **Step 4: Hook it to Recents opening and leaving**

Dans `ForegroundAppService`, à l'événement de fenêtre de Récents (`if (recents)`), avec `d = displayId ?: focusedDisplayId()` :
- l'appel est `RecentsScreen.show(this, d, origin, launcherPackages + packageName) { (if (d == PRIMARY_DISPLAY) secondDisplayId() else PRIMARY_DISPLAY)?.let { displayApps[it] } }` ;
- une liste déjà connue (`RecentsSource.tasks` non vide) : `show` tout de suite, puis `RecentsSource.refreshAsync(this)` la met à jour ;
- aucune liste encore (premier lancement) : `refreshAsync(this) { ok -> … }`, et `show` seulement si `ok`, si la liste n'est pas vide et si `recentsDisplay == d` (Récents encore ouvert là). Sinon, rien : l'écran d'Android reste ;
- `origin` est `recentsOrigin` s'il est récent (moins de 3 s, comme dans `enterRecents`) et ni `HOME_ORIGIN` ni `WAYFINDER_ORIGIN`, sinon `displayApps[d]`.

Pour la fermeture :
- `exitRecents()` appelle `RecentsScreen.close()` ;
- la branche « event on $displayId but not on top — ignored » de `enterRecents` aussi : c'est le balayage qui finit à l'accueil ;
- `onDestroy` aussi.

- [ ] **Step 5: Build, install, unit tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`, puis `adb install -r` de l'APK de débogage.
Expected: tests verts (30), installation `Success`.

- [ ] **Step 6: On the Thor — opening and leaving**

Pour chaque essai : une action, puis une capture (`screencap` de l'écran du haut).
1. Ouvrir Paramètres, faire le geste : le multitâche est affiché, Paramètres est au centre avec la bande de sélection.
   - C'est aussi la première ouverture après l'installation (Review Focus 2) : jamais un écran vide.
2. Refaire le geste sans pause (`input swipe 960 1079 960 450 300`) : à la fin, l'accueil est à l'écran, sans le multitâche (Review Focus 1).

Expected : comme décrit ; `logcat` sans `FATAL` ni `ANR in app.wayfinder`.

- [ ] **Step 7: On the Thor — each touch action**

Pour chaque action : une action, puis une capture, sur les cartes Paramètres et Wayfinder.
1. Toucher la carte Paramètres : Paramètres s'ouvre et le multitâche disparaît.
2. Badge de la carte Paramètres : elle passe sur l'écran du bas (badge `2`). Ensuite `am stack remove` de la tâche.
3. Cadenas : l'icône passe à `Lock`. Le toucher de nouveau la remet à `LockOpen`.
4. Screenshot : un fichier est apparu dans `/sdcard/Pictures/Screenshots`.
5. Glisser la carte Paramètres vers le haut : elle disparaît et `dumpsys activity recents` ne la liste plus. S'il ne reste aucune carte : l'accueil (Review Focus 3).
6. Toucher hors des cartes : retour à l'appli d'avant.

« Clear all » n'est pas essayé sur le Thor de Thomas : il fermerait ses applis. La règle de ce qu'il garde est déjà testée.

Expected : comme décrit. Remettre l'état : tâche Paramètres fermée, capture d'essai supprimée.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/wayfinder/RecentsScreen.kt app/src/main/java/app/wayfinder/ForegroundAppService.kt
git commit -m "Multitask in iiSU's look over Android's Recents (touch)"
```

---

### Task 4: À la manette

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (`recentsKey`, `recentsBrowse`, `recentsSelect`, `recentsGoBack`)

**Interfaces:**
- Consumes: `RecentsScreen.shownOn`, `browse`, `openSelected`, `closeSelected`, `clearAll`, `back` (tâche 3).

- [ ] **Step 1: Route the controller to the screen while it shows on Recents' display**

Seulement quand `RecentsScreen.shownOn == recentsDisplay`. Sinon, le comportement d'aujourd'hui sur l'écran d'Android.
- A → `openSelected()`.
- Y, X → `closeSelected()`.
- Au début de `recentsBrowse(dir)` (L1/R1 et croix gauche/droite) → `browse(dir)`, et retour.
- Le second Select dans `recentsSelect` → `clearAll()` au lieu de `clickRecentsButton`.
- Start → `RecentsScreen.close()`, puis le chemin actuel.
- `recentsGoBack` : si l'origine est `WAYFINDER_ORIGIN`, le chemin actuel (retour au Hub) après `RecentsScreen.close()`. Sinon `if (RecentsScreen.back()) { exitRecents(); return }`, puis le chemin actuel sans le toucher de carte d'Android : `exitRecents()` et `GLOBAL_ACTION_BACK`.

L'aide en haut de l'écran (`Held.RECENTS`) ne change pas.

- [ ] **Step 2: Build and install**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`, puis `adb install -r`.
Expected: tests verts, `Success`.

- [ ] **Step 3: On the Thor**

Ouvrir Paramètres puis Récents par le geste ; une touche à la fois, une capture après chacune :
- `input gamepad keyevent KEYCODE_BUTTON_L1` : la carte de gauche est choisie ;
- `KEYCODE_BUTTON_R1` : retour à Paramètres ;
- `KEYCODE_BUTTON_B` : Paramètres revient au premier plan ;
- rouvrir Récents, puis `KEYCODE_BUTTON_A` : Paramètres s'ouvre ;
- rouvrir, puis `KEYCODE_BUTTON_Y` : la carte Paramètres est fermée ;
- rouvrir depuis l'accueil, puis `KEYCODE_BUTTON_START` : l'accueil.

Si la manette simulée n'est pas reconnue par `ButtonEngine.menuButton`, utiliser `sendevent` sur le nœud de la manette du Thor (`getevent -pl` pour le trouver) et le noter dans le journal.

Expected : comme décrit. Remettre l'état (tâche Paramètres fermée).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/wayfinder/ForegroundAppService.kt
git commit -m "Multitask: the controller drives the iiSU carousel"
```

---

### Task 5: Délai d'apparition, écran du bas, thèmes, version finale

**Files:**
- Modify: `app/src/main/res/xml/accessibility_service_config.xml` (`notificationTimeout`)

- [ ] **Step 1: Measure the cover's delay at 100 ms**

Run : ouvrir Paramètres ; `screenrecord --display-id 4630946441858561667 --time-limit 5`, puis le geste ; extraire les images à 30 i/s (ffmpeg sur le PC).
Expected : noter le numéro de l'image où le multitâche apparaît, compté depuis la première image où la carte d'Android rétrécit.

- [ ] **Step 2: `notificationTimeout` to 0, measure again**

Run : `android:notificationTimeout="0"`, compiler, installer, même mesure.
Expected : le multitâche apparaît plus tôt, sans `ANR in app.wayfinder` dans `logcat` après dix minutes d'usage (ouvrir et fermer Paramètres, le panneau rapide, la barre de volume). Sinon : retour à 100, avec une ligne `Ruling:` dans le journal.

- [ ] **Step 3: Many cards**

Run : ouvrir huit applis de test sur l'écran du haut (Paramètres, Fichiers, Chrome, Horloge, Calculatrice, Contacts, Agenda, Galerie), puis le geste. Faire défiler de bout en bout avec `KEYCODE_BUTTON_R1` / `L1`.
Expected : les aperçus arrivent, défilement sans saccade visible à la capture vidéo. Puis fermer ces tâches une par une (`am stack remove`), sauf celles que Thomas avait déjà.

- [ ] **Step 4: Bottom screen**

Run : ouvrir Paramètres sur l'écran du bas (`am start --display 4 -a android.settings.SETTINGS`), puis le même geste avec `input -d 4` aux coordonnées de cet écran (`wm size -d 4`, bord du bas).
Expected : si Récents s'ouvre en bas, le multitâche s'y affiche ; sinon, noter que le geste n'existe pas en bas. Remettre l'état.

- [ ] **Step 5: Light and dark**

Run : lire `cmd uimode night`. Ouvrir le multitâche dans ce thème, capture. Passer à l'autre (`cmd uimode night yes|no` ; iiSU suit le système), rouvrir, capture. Puis remettre la valeur lue.
Expected : fond, cartes et texte suivent iiSU dans les deux thèmes.

- [ ] **Step 6: Release build and commit**

Run : `./gradlew :app:testDebugUnitTest :app:assembleRelease`, `adb install -r app/build/outputs/apk/release/app-release.apk`, puis un dernier geste et une capture.
Expected : tests verts, `Success`, le multitâche s'affiche dans la version finale.

```bash
git add app/src/main/res/xml/accessibility_service_config.xml
git commit -m "Multitask: Recents' window event at once (no notification delay)"
```
