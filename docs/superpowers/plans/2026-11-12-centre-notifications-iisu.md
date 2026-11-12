# Centre de notifications au style iiSU — plan de réalisation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Le balayage du haut ouvre le centre de notifications de Wayfinder (volet droit au style iiSU : interrupteurs Android, notifications avec leurs boutons) à la place du volet d'Android, bloqué.

**Architecture:**
- Le service d'accessibilité bloque le volet d'Android par le root (`cmd statusbar send-disable-flag`) et pose une bande tactile de 20 dp en haut de l'écran du haut. Il rétablit le volet à l'arrêt.
- Un `NotificationListenerService` tient la liste des notifications.
- `NotificationCenter`, une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` en Compose, dessine le volet avec des briques communes (carte de menu, tuile à bascule) que le centre de contrôle reprendra ensuite.

**Tech Stack:** Kotlin, Jetpack Compose, `NotificationListenerService`, `WindowManager`, root via pservice (`cmd statusbar`, `cmd notification`), JUnit 4, adb (`cmd notification post`, `input motionevent`, `sendevent`).

**Spec:** `docs/superpowers/specs/2026-11-12-centre-notifications-iisu-design.md`

## Global Constraints

- Branche `iisu-look`, commits en anglais, sans URL de session, finis par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` ; poussés sur `origin` (le fork) seulement, jamais sur `upstream`.
- Couleurs, arrondis et police uniquement par le thème (`LocalGlass`, `Radii`, `IisuLook`) ; aucune nouvelle dépendance.
- Aucun appel root sur le fil principal.
- Tout chemin d'arrêt rend le volet d'Android : `onDestroy`, `turnOff()`, le réglage du Hub coupé, l'absence de root.
- Compilation : `./gradlew :app:assembleRelease` (version finale) ou `:app:assembleDebug` (journaux, `run-as`) ; même clé, `adb install -r`. La version finale est réinstallée à la fin.
- Sur le Thor :
  - une action, une capture ;
  - vérifier l'appli au premier plan avant tout geste ; ne jamais toucher GameNative ;
  - aucun geste de bord sur les réglages d'iiSU ;
  - ne pas changer le thème du système (`cmd uimode`) : il fait planter Quickstep. Le thème d'iiSU suffit, remis sur System ensuite ;
  - toute notification de test est postée par `cmd notification post`, puis effacée ;
  - le volume et la sonnerie de Thomas ne sont pas touchés.
- La manette du Thor : `sendevent` sur `/dev/input/event9` (304 A, 305 B, 307/308 boutons du haut et de gauche, 314 Select, 315 Start ; croix : `3 16 ±1` / `3 17 ±1`).

## Review Focus

1. Wayfinder coupé (« Turn Wayfinder off »), désactivé dans les réglages d'accessibilité ou tué : le volet d'Android revient. Vérification : tâche 1, étapes 6 et 7.
2. Un toucher bref tout en haut d'un jeu : rien ne s'ouvre, seul un glissement vers le bas ouvre le centre. Test : `pullDown_needsADownwardSwipe` (tâche 1).
3. Une notification sans titre ni texte, une très longue, une cinquantaine : la carte tient (nom de l'appli seul ; trois lignes au plus), la liste défile sans saccade. Vérification : tâche 4, étape 6.
4. Une notification qui arrive ou part pendant que le centre est ouvert : la liste suit sans se fermer. Vérification : tâche 4, étape 6.
5. Une carte dont le `PendingIntent` est annulé : pas de plantage, le centre se ferme. Test par lecture du code (`CanceledException` attrapée), tâche 4, étape 3.

---

### Task 1: Bloquer le volet d'Android, capter le glissement

**Files:**
- Create: `app/src/main/java/app/wayfinder/NotificationGesture.kt`
- Create: `app/src/main/java/app/wayfinder/NotificationModel.kt` (première règle : le glissement)
- Modify: `app/src/main/java/app/wayfinder/Features.kt`, `app/src/main/java/app/wayfinder/Hub.kt` (le réglage)
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (démarrage, arrêt)
- Test: `app/src/test/java/app/wayfinder/NotificationModelTest.kt`

**Interfaces:**
- Produces:
  - `object NotificationModel { fun pullDown(dy: Float, dx: Float, minPx: Float): Boolean }` : vrai pour un glissement vers le bas d'au moins [minPx], plus vertical qu'horizontal ;
  - `object NotificationGesture { fun start(service: AccessibilityService, onPull: () -> Unit); fun stop(ctx: Context) }` : `start` bloque le volet d'Android (fil à part) et pose la bande ; `stop` retire la bande et rétablit le volet ;
  - `Features.notifications: Boolean` (préférence `feat_notifs`, vrai par défaut) et `Features.enableNotifications(on: Boolean)`.

- [ ] **Step 1: Probe the blocking alone on the Thor**

Run, par adb :
- `cmd statusbar send-disable-flag statusbar-expansion` ;
- puis le geste `input -d 0 motionevent DOWN 960 2`, `MOVE 960 200`, `MOVE 960 500`, `UP 960 500` sur l'accueil d'iiSU (page de la grille, pas ses réglages) ;
- `dumpsys window windows | grep -A30 NotificationShade | grep isVisible` ;
- puis `cmd statusbar send-disable-flag none` et le même geste.

Expected : bloqué, `isVisible=false` ; rétabli, `isVisible=true`. Refermer avec `cmd statusbar collapse`. Si le blocage n'agit pas, s'arrêter : l'approche entière en dépend (le dire à Thomas).

- [ ] **Step 2: Write the failing test**

```kotlin
class NotificationModelTest {
    @Test fun pullDown_needsADownwardSwipe() {
        assertEquals(true, NotificationModel.pullDown(dy = 120f, dx = 10f, minPx = 90f))
        assertEquals(false, NotificationModel.pullDown(dy = 40f, dx = 0f, minPx = 90f))    // too short: a tap or a nudge
        assertEquals(false, NotificationModel.pullDown(dy = 120f, dx = 200f, minPx = 90f)) // sideways
        assertEquals(false, NotificationModel.pullDown(dy = -150f, dx = 0f, minPx = 90f))  // upward
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests app.wayfinder.NotificationModelTest`
Expected: FAIL, `Unresolved reference: NotificationModel`.

- [ ] **Step 4: Implement `NotificationModel.pullDown`**

`dy >= minPx && dy > abs(dx)`.

- [ ] **Step 5: Implement `NotificationGesture`, the setting and the hooks**

- `start` : sur un fil à part, si `PServiceBridge.isAvailable()`, `PServiceBridge.exec("cmd statusbar send-disable-flag statusbar-expansion")`. Puis, sur le fil principal, la bande :
  - fenêtre `TYPE_ACCESSIBILITY_OVERLAY` sur l'écran 0, `MATCH_PARENT` × 20 dp, `Gravity.TOP` ;
  - `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`, transparente, titre `WayfinderPullDown` ;
  - son `onTouchEvent` suit un doigt posé dans la bande (`ACTION_DOWN` → `ACTION_MOVE`) et appelle `onPull` une fois, dès que `pullDown(dy, dx, 40 dp)`.
  
  Sans root : ni blocage ni bande.
- `stop` : retire la bande, puis `send-disable-flag none` sur un fil à part.
- `ForegroundAppService` :
  - fin de `onServiceConnected` : `if (Features.notifications) NotificationGesture.start(this) { /* tâche 4 : ouvrir le centre */ }` ;
  - `onDestroy` et `turnOff()` : `NotificationGesture.stop(this)`.
- `Features.enableNotifications(on)` enregistre la préférence et démarre ou arrête le geste par le service. `enableAll` / `disableAll` (Features.kt, lignes 96 et 105) l'incluent.
- Hub, carte des parties (à côté de « Stick lights ») : `SettingCard("Notification center", if (Features.notifications) "iiSU's notification center on a swipe down from the top" else "Off — Android's", checked = Features.notifications, onChecked = { Features.enableNotifications(it) })`.

- [ ] **Step 6: On the Thor — the gesture blocked, the strip, the restore**

Run : installer la version de débogage. Sur l'accueil d'iiSU (grille), faire le geste de l'étape 1 et relever `NotificationShade isVisible` et `logcat | grep -i pull`.
Expected : le volet d'Android ne s'ouvre pas, et le journal montre que `onPull` a été appelé (la tâche 4 l'ouvrira).

- [ ] **Step 7: On the Thor — every way back to Android's shade**

Run :
1. Couper « Notification center » dans le Hub, refaire le geste.
2. Rallumer, puis « Turn Wayfinder off » (panneau rapide, deux appuis), refaire le geste.
3. Relancer Wayfinder.

Expected : 1 et 2 ouvrent le volet d'Android, qui est ensuite refermé (`cmd statusbar collapse`) ; 3 le bloque de nouveau.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/wayfinder/NotificationGesture.kt app/src/main/java/app/wayfinder/NotificationModel.kt app/src/main/java/app/wayfinder/Features.kt app/src/main/java/app/wayfinder/Hub.kt app/src/main/java/app/wayfinder/ForegroundAppService.kt app/src/test/java/app/wayfinder/NotificationModelTest.kt
git commit -m "Notification center: Android's shade blocked, a top strip takes the swipe"
```

---

### Task 2: Les notifications (lecteur et règles)

**Files:**
- Create: `app/src/main/java/app/wayfinder/NotificationsListener.kt`
- Modify: `app/src/main/java/app/wayfinder/NotificationModel.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (l'accès)
- Test: `app/src/test/java/app/wayfinder/NotificationModelTest.kt`

**Interfaces:**
- Consumes: `NotificationModel` (tâche 1).
- Produces:
  - `data class NotifAction(val title: String, val index: Int)` : un bouton sans réponse écrite, [index] dans `notification.actions` ;
  - `data class NotifItem(val key: String, val pkg: String, val postTime: Long, val title: String?, val text: String?, val clearable: Boolean, val group: String?, val summary: Boolean, val actions: List<NotifAction>)` ;
  - `NotificationModel.visible(items: List<NotifItem>): List<NotifItem>` : les résumés de groupe retirés quand une notification du même groupe est là, la plus récente d'abord ;
  - `NotificationModel.anyClearable(items: List<NotifItem>): Boolean` ;
  - `object NotificationsListener` (le service : `class NotificationsListener : NotificationListenerService`, son compagnon) : `items: List<NotifItem>` (état Compose), `open(key)`, `dismiss(key)`, `clearAll()`, `act(key, index)` ; toutes sur le fil principal, sans effet si le service n'est pas connecté.

- [ ] **Step 1: Write the failing tests**

```kotlin
    private fun n(key: String, at: Long, group: String? = null, summary: Boolean = false, clearable: Boolean = true) =
        NotifItem(key, "app.x", at, "t", "x", clearable, group, summary, emptyList())

    @Test fun visible_hidesASummaryWithItsChildren_newestFirst() {
        val items = listOf(n("a", 10, group = "g", summary = true), n("b", 20, group = "g"), n("c", 30), n("d", 5, group = "h", summary = true))
        assertEquals(listOf("c", "b", "d"), NotificationModel.visible(items).map { it.key })   // "d": a summary alone stays
    }

    @Test fun anyClearable_ignoresOngoing() {
        assertEquals(false, NotificationModel.anyClearable(listOf(n("a", 1, clearable = false))))
        assertEquals(true, NotificationModel.anyClearable(listOf(n("a", 1, clearable = false), n("b", 2))))
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests app.wayfinder.NotificationModelTest`
Expected: FAIL, `Unresolved reference: NotifItem`.

- [ ] **Step 3: Implement `NotifItem`, `NotifAction`, `visible`, `anyClearable`**

- [ ] **Step 4: Implement the listener and its access**

- Le manifeste déclare `NotificationsListener` :
  - `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` ;
  - l'intent-filter `android.service.notification.NotificationListenerService` ;
  - `android:exported="true"`.
- Conversion d'un `StatusBarNotification` en `NotifItem` :
  - titre : `EXTRA_TITLE` ;
  - texte : `EXTRA_BIG_TEXT`, sinon `EXTRA_TEXT` ;
  - `clearable` : `isClearable` ;
  - groupe : `groupKey` ; résumé : `FLAG_GROUP_SUMMARY` ;
  - boutons : chaque `notification.actions[i]` sans `remoteInputs`.
- `items` se relit en entier sur `onListenerConnected`, `onNotificationPosted` et `onNotificationRemoved`, sur le fil principal.
- `open(key)` : `contentIntent.send()` dans un `try` (`CanceledException` → rien) ; puis, si `FLAG_AUTO_CANCEL`, `cancelNotification(key)`.
- `dismiss(key)` : `cancelNotification(key)`. `clearAll()` : `cancelAllNotifications()`. `act(key, index)` : `actions[index].actionIntent.send()` dans le même `try`.
- Accès : à la connexion du service d'accessibilité, si `NotificationManager.isNotificationListenerAccessGranted(ComponentName(this, NotificationsListener::class.java))` est faux, `PServiceBridge.exec("cmd notification allow_listener app.wayfinder/app.wayfinder.NotificationsListener")` sur un fil à part.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (NotificationModelTest 3 et les 32 d'avant).

- [ ] **Step 6: On the Thor — the listener reads**

Run :
- installer la version de débogage ;
- `cmd notification post -S bigtext -t "Wayfinder test" wf_test "Une notification de test"` ;
- lire `logcat | grep ThorNotifs`, où une ligne `Log.d` liste les clés après chaque relecture ;
- puis `cmd notification` pour l'effacer (ou `dismiss` depuis la tâche 4).

Expected : la notification de test est dans la liste ; `settings get secure enabled_notification_listeners` contient Wayfinder et toujours iiSU.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/wayfinder/NotificationsListener.kt app/src/main/java/app/wayfinder/NotificationModel.kt app/src/main/AndroidManifest.xml app/src/main/java/app/wayfinder/ForegroundAppService.kt app/src/test/java/app/wayfinder/NotificationModelTest.kt
git commit -m "Notification center: Wayfinder reads the notifications"
```

---

### Task 3: Briques communes (carte de menu, tuile à bascule)

**Files:**
- Create: `app/src/main/java/app/wayfinder/ui/IisuPanel.kt`
- Modify: `app/src/main/java/app/wayfinder/VolumeBar.kt` (s'en sert ; même rendu)

**Interfaces:**
- Produces:
  - `fun menuCard(g: GlassColors): Color` : `IisuLook.MENU_LIGHT` en clair, `g.surfaceModal` en sombre (déplacé de `VolumeBar.menuCard`) ;
  - `@Composable fun IisuToggleTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, height: Dp = 72.dp, onClick: () -> Unit)` : la case à icône et nom, allumée remplie de `Brush.linearGradient(g.accentStops)` (encre `g.onAccent`), éteinte sur `glassSurface(... well = g.surfaceModal, veil = false)` ; coins 16 dp ; focusable, avec la bande de sélection (`focusIndicator`).

- [ ] **Step 1: Move `menuCard`, write `IisuToggleTile` from the sound panel's ringer tiles, and use both in `VolumeBar`**

- [ ] **Step 2: Build, install, compare**

Run : `./gradlew :app:testDebugUnitTest :app:assembleDebug`, installer. Ouvrir la barre de volume par Volume− seulement si le volume est à 0 ; sinon par Volume− puis Volume+ (le volume revient où il était). Toucher « … », capturer.
Expected : tests verts ; le panneau du son est identique à la capture d'avant (cases, carte, curseurs).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/wayfinder/ui/IisuPanel.kt app/src/main/java/app/wayfinder/VolumeBar.kt
git commit -m "iiSU panel bricks: the menu card and the toggle tile, shared"
```

---

### Task 4: Le centre, au toucher

**Files:**
- Create: `app/src/main/java/app/wayfinder/NotificationCenter.kt`
- Modify: `app/src/main/java/app/wayfinder/ForegroundAppService.kt` (`onPull` ouvre le centre)

**Interfaces:**
- Consumes: `NotificationsListener` et `NotificationModel` (tâche 2), `menuCard` et `IisuToggleTile` (tâche 3), `PanelShortcuts.tiles(ctx, close, after, refresh)` (ids `wifi`, `bt`, `dnd`, `rotate`, `airplane`, `location`, `dark`, `night`, `saver`).
- Produces: `object NotificationCenter { fun show(service: AccessibilityService): Boolean; fun close(); val isOpen: Boolean }`, sur le fil principal.

- [ ] **Step 1: The window**

- Comme `QuickPanelWindow` : écran 0, `ContextThemeWrapper(..., R.style.Theme_Wayfinder_Glass)`, `ComposeOwner`, protection par `onViewDetachedFromWindow` ; `TYPE_ACCESSIBILITY_OVERLAY`, `MATCH_PARENT` × `MATCH_PARENT`, focusable, `FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED`, titre `WayfinderNotifications`.
- La racine ferme sur Retour / B relâchés et non annulés.
- `IisuLookSource.refreshAsync` à l'ouverture.
- Si l'ajout échoue : `NotificationGesture.stop`, pour que le volet d'Android revienne.

- [ ] **Step 2: The layout**

- Dans `ThorGlassTheme(dark = GlassTokens.isDark(ctx)) { CappedFontScale { … } }`, une `Row` pleine :
  - à gauche, 40 % de voile (`Color.Black` à 25 %) : son toucher ferme ;
  - à droite, 60 % : le volet, `menuCard(g)`, coins de 20 dp à gauche, `padding` de 20 dp.
- Le volet glisse depuis la droite en 200 ms (`animateFloatAsState` sur un décalage), à l'ouverture comme à la fermeture.
- Dans le volet, une `LazyColumn` :
  - les tuiles : neuf `IisuToggleTile` de 64 dp sur une grille de cinq colonnes, leurs noms et états venant de `PanelShortcuts.tiles`, relus sur leur `refresh` ;
  - la rangée « Notifications » (`titleMedium`) + `GlassButton("Clear notifications", icon = Icons.Rounded.DeleteSweep)`, grisé si `!anyClearable` ;
  - une carte par élément de `NotificationModel.visible(NotificationsListener.items)` :
    - fond `g.surfaceModal`, coins de 20 dp ;
    - icône de l'appli 48 dp ronde ;
    - nom de l'appli en `titleSmall` gras et `DateUtils.getRelativeTimeSpanString(postTime, now, MINUTE_IN_MILLIS)` en `bodySmall` ;
    - titre en `titleSmall` ; texte en `bodyMedium`, trois lignes au plus ;
    - les boutons en `GlassButton(size = small)` ;
  - vide : la cloche (`Icons.Rounded.Notifications`) et « No notifications ».

- [ ] **Step 3: The actions**

- Toucher une carte : `open(key)`, puis `close()`.
- Glisser une carte de côté au-delà de 30 % de sa largeur (`detectHorizontalDragGestures`) : `dismiss(key)`. Seulement si `clearable` ; une carte permanente ne glisse pas.
- « Clear notifications » : `clearAll()`.
- Un bouton : `act(key, index)`, puis `close()`.
- Une tuile : son `action` de `PanelShortcuts`.
- Glisser le volet vers le haut au-delà de 80 dp : `close()`.

- [ ] **Step 4: Open on the pull**

Dans `ForegroundAppService`, l'`onPull` de la tâche 1 appelle `NotificationCenter.show(this)` (déjà ouvert : rien).

- [ ] **Step 5: Build, install, unit tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`, installer.
Expected: tests verts, `Success`.

- [ ] **Step 6: On the Thor — each touch action**

Préparation : poster trois notifications de test.
- `cmd notification post -S bigtext -t "Test long" wf_a "<un texte de 400 caractères>"` ;
- `cmd notification post -t "" wf_b ""` ;
- `cmd notification post -t "Test" wf_c "court"`.

Ensuite, une action puis une capture à chaque fois :
1. Le geste depuis l'accueil d'iiSU : le volet glisse depuis la droite ; les tuiles, puis les trois cartes (la longue sur trois lignes, la vide avec le nom de l'appli seul).
2. Poster une quatrième notification pendant que le centre est ouvert : elle apparaît en tête. En effacer une par adb : elle disparaît.
3. Glisser `wf_c` sur le côté : elle part.
4. Toucher la tuile Rotation deux fois : elle s'allume puis s'éteint, et `settings get system accelerometer_rotation` suit. L'état d'origine est rétabli.
5. « Clear notifications » : toutes les notifications de test partent ; les permanentes (celle de Wayfinder) restent.
6. Une carte avec un bouton (une notification système qui en a, ou à défaut aucune : le noter) : le bouton agit.
7. Toucher le voile : fermé. Rouvrir, glisser le volet vers le haut : fermé.
8. Poster cinquante notifications (`for i in $(seq 50)` en une commande adb), ouvrir, faire défiler : pas de saccade à la vidéo ; tout effacer.

Expected : comme décrit ; aucune notification de test ne reste.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/wayfinder/NotificationCenter.kt app/src/main/java/app/wayfinder/ForegroundAppService.kt
git commit -m "Notification center in iiSU's look (touch)"
```

---

### Task 5: À la manette, l'action « Notifications », version finale

**Files:**
- Modify: `app/src/main/java/app/wayfinder/NotificationCenter.kt`, `app/src/main/java/app/wayfinder/ActionRegistry.kt`, `app/src/main/java/app/wayfinder/ControlsScreen.kt`, `app/src/main/java/app/wayfinder/ForegroundAppService.kt`

**Interfaces:**
- Consumes: `NotificationCenter` (tâche 4).
- Produces: `ThorAction.NOTIFICATIONS("Notifications", "Open the notification center")`, dans le groupe « Screens & apps » des contrôles, liée à aucun bouton par défaut.

- [ ] **Step 1: The controller**

- La racine du centre reprend celle de `QuickPanelWindow.Root` : `PadNav` pour le stick et la croix, `PadFallbackFilter`, `KEYCODE_BUTTON_MODE` avalé.
- À l'ouverture, le service donne le focus à l'écran 0 comme pour le panneau rapide (`focusPanel(0, 0)`) ; à la fermeture, il le rend comme `onQuickPanelClosed`.
- X (`KEYCODE_BUTTON_X`) sur une carte focalisée : `dismiss(key)`.
- Select deux fois en 2,5 s : `clearAll()`. Le premier appui fait dire « Select again: clear all » au bouton, comme dans le multitâche.
- `NOTIFICATIONS` dans `ActionRegistry.perform` : `svc.handler.post { NotificationCenter.show(svc) }`.

- [ ] **Step 2: Build, install**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`, installer.
Expected: tests verts, `Success`.

- [ ] **Step 3: On the Thor — the controller**

Poster deux notifications de test, ouvrir le centre par le geste. Puis, une touche à la fois et une capture après chacune :
- la croix vers le bas : la bande de sélection passe des tuiles au bouton puis aux cartes ;
- X sur une carte : elle part ;
- Select une fois : le bouton dit « Select again: clear all » ; ne pas confirmer, attendre 2,5 s ;
- A sur une tuile : elle bascule (la rebasculer) ;
- B : fermé.

Expected : comme décrit ; les notifications de test effacées à la fin.

- [ ] **Step 4: On the Thor — dark, release**

Run :
- passer le thème d'iiSU sur Dark, ouvrir le centre, capturer ; le remettre sur System ;
- `./gradlew :app:assembleRelease`, `adb install -r` de la version finale ;
- un dernier geste et une capture.

Expected : clair et sombre suivent iiSU ; la version finale ouvre le centre.

- [ ] **Step 5: Commit and push**

```bash
git add app/src/main/java/app/wayfinder/NotificationCenter.kt app/src/main/java/app/wayfinder/ActionRegistry.kt app/src/main/java/app/wayfinder/ControlsScreen.kt app/src/main/java/app/wayfinder/ForegroundAppService.kt
git commit -m "Notification center: the controller, and a Notifications action"
git push origin iisu-look
```
