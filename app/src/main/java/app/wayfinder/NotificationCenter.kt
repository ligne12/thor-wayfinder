package app.wayfinder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.wayfinder.ui.ButtonSize
import app.wayfinder.ui.CappedFontScale
import app.wayfinder.ui.GlassButton
import app.wayfinder.ui.GlassTokens
import app.wayfinder.ui.IisuToggleTile
import app.wayfinder.ui.LocalGlass
import app.wayfinder.ui.ThorGlassTheme
import app.wayfinder.ui.focusIndicator
import app.wayfinder.ui.glassSurface
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import app.wayfinder.ui.menuCard

/**
 * iiSU fork: the notification center, opened by a pull down from the top ([NotificationGesture]) in place of Android's
 * shade. iiSU's notification panel: a sheet on the right (60 % of the top screen) on iiSU's menu card, the game still in
 * view on the left under a light veil — Android's switches as tiles, then the notifications as iiSU draws them (app icon,
 * name and age, title, text, the app's buttons). Tap a card to open it, swipe it aside to clear it; "Clear notifications";
 * a tap on the veil, a pull up on the sheet's top, Back or B close it. Main thread.
 */
object NotificationCenter {
    private const val TAG = "ThorNotifs"
    private val TILES = listOf("wifi", "bt", "dnd", "rotate", "airplane", "location", "dark", "night", "saver")
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var view: android.view.View? = null
    private var wm: WindowManager? = null
    private var owner: QuickPanelWindow.PanelOwner? = null
    private var shown by mutableStateOf(false)   // the sheet in (true) or sliding out (false: the window goes once it is out)
    private var tick by mutableIntStateOf(0)    // the tiles read again (a switch writes through root, a moment later)
    private var clearArmed by mutableStateOf(false)   // the controller's first Select: "Clear notifications" asks for the second
    private var clearAt = 0L

    val isOpen: Boolean get() = view != null

    // each app's name and icon, read once (a card read them again each time it scrolled into view)
    private val apps = HashMap<String, Pair<String, androidx.compose.ui.graphics.ImageBitmap?>>()
    private fun appOf(ctx: Context, pkg: String) = apps.getOrPut(pkg) {
        val pm = ctx.packageManager
        (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) to
            runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    /** Opens it on the top screen; false if the window couldn't be added (Android's shade is given back then). */
    fun show(service: AccessibilityService): Boolean {
        if (view != null) { shown = true; return true }
        val d = service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0) ?: return false
        val ctx = android.view.ContextThemeWrapper(overlayContext(service, d), R.style.Theme_Wayfinder_Glass)
        val wm = ctx.getSystemService(WindowManager::class.java)
        NotificationsListener.reconnectIfNeeded(service)
        app.wayfinder.ui.IisuLookSource.refreshAsync(service)
        // iiSU fork: the quick panel's owner — a Back dispatcher, so B closes a tile's options window before the center
        val owner = QuickPanelWindow.PanelOwner()
        val root = object : android.widget.FrameLayout(ctx) {
            // as the quick panel's root: the stick / D-pad move the focus, R3 / L3 / Start never "click"
            private val nav = app.wayfinder.ui.PadNav({ getChildAt(0) }, { dispatchKeyEvent(it) })
            private val padFallback = app.wayfinder.ui.PadFallbackFilter()
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        if (owner.onBackPressedDispatcher.hasEnabledCallbacks()) owner.onBackPressedDispatcher.onBackPressed() else close()
                    }
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) return true
                // Select twice within 2.5 s: clear them all (the first says so on the button)
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) selectPressed()
                    return true
                }
                if (padFallback.shouldDrop(event)) return true
                return super.dispatchKeyEvent(event)
            }
            override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean = nav.onMotion(ev) || super.dispatchGenericMotionEvent(ev)
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (!hasWindowFocus) nav.stop() }
            override fun onDetachedFromWindow() { nav.stop(); super.onDetachedFromWindow() }
        }
        owner.base.attach(root)
        root.setViewTreeOnBackPressedDispatcherOwner(owner)
        root.addView(androidx.compose.ui.platform.ComposeView(ctx).apply {
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owner) {
                    app.wayfinder.ui.IisuLookSource.Follow(ctx)
                    // iiSU fork: WindowRoot — the options window's sheet host and Back layer, as in the quick panel
                    ThorGlassTheme(dark = GlassTokens.isDark(ctx)) { CappedFontScale { app.wayfinder.ui.WindowRoot { Center(ctx) } } }
                }
            }
        })
        root.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}
            override fun onViewDetachedFromWindow(v: android.view.View) { if (view === root) remove() }   // removed by the system
        })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // focusable: Android's Back and the controller come here
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "WayfinderNotifications"
        }
        return try {
            wm.addView(root, lp)
            view = root; this.wm = wm; this.owner = owner
            main.post { shown = true }   // slides in from its first frame
            true
        } catch (t: Throwable) {
            owner.base.destroy()
            android.util.Log.w(TAG, "addView: ${t.javaClass.simpleName}: ${t.message}")
            NotificationGesture.stop()   // no center: Android's shade back
            false
        }
    }

    private fun selectPressed() {
        val now = android.os.SystemClock.uptimeMillis()
        if (clearArmed && now - clearAt <= 2500) { clearArmed = false; NotificationsListener.clearAll(); return }
        clearArmed = true; clearAt = now
        main.postDelayed({ if (android.os.SystemClock.uptimeMillis() - clearAt >= 2500) clearArmed = false }, 2500)
    }

    /** Slides the sheet out; the window goes once it is out. */
    fun close() { if (view != null) shown = false }

    private fun remove() {
        val v = view ?: return
        view = null
        runCatching { wm?.removeViewImmediate(v) }
        owner?.base?.destroy()
        wm = null; owner = null; shown = false; clearArmed = false
        ForegroundAppService.notificationCenterClosed()   // the controller back where it was
    }

    @Composable
    private fun Center(ctx: Context) {
        val g = LocalGlass.current
        // 0: in; 1: out above the screen. Out again after a close: the window goes
        val out by animateFloatAsState(if (shown) 0f else 1f, tween(200), label = "sheet",
            finishedListener = { if (it >= 1f && !shown) remove() })
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // the veil: the game stays in view; a tap closes
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out }
                .background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { close() } })
            // iiSU fork: the sheet comes down from the top, full width, as tall as its content up to 85 % of the screen
            Box(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.85f).graphicsLayer { translationY = -out * size.height }
                .background(menuCard(g), RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .pointerInput(Unit) { detectTapGestures { } }   // a tap on the sheet itself stays there (the veil under it closes)
                .padding(start = 20.dp, top = 36.dp, end = 20.dp, bottom = 20.dp)) {   // Thomas: the tiles looked stuck to the top
                Sheet(ctx)
            }
        }
    }

    @Composable
    private fun Sheet(ctx: Context) {
        val g = LocalGlass.current
        val density = LocalDensity.current
        val t = tick
        val tiles = remember(t) {
            PanelShortcuts.tiles(ctx, close = { close() }, after = { ms, act -> close(); main.postDelayed(act, ms) },
                refresh = { ms -> main.postDelayed({ tick++ }, ms) })
        }
        val items = NotificationModel.visible(NotificationsListener.items)
        var pull by remember { mutableFloatStateOf(0f) }
        // iiSU fork: the tile whose options window is open (a long press) — out here: the grid's items come and go
        var optionsFor by remember { mutableStateOf<String?>(null) }
        // a pull up on the tiles or the title closes the sheet (the cards below scroll); the tiles alone sit too near the
        // top for 80 dp of travel
        val pullUp = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = { if (pull < -with(density) { 80.dp.toPx() }) close(); pull = 0f },
                onDragCancel = { pull = 0f }) { _, dy -> pull += dy }
        }
        // iiSU fork: the cards in two columns, filled row by row (the newest top left); the handle under them says up closes
        Column {
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().then(pullUp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((id, tile) in TILES.mapNotNull { id -> tiles[id]?.let { id to it } }) IisuToggleTile(tile.icon, tile.label, tile.on,
                            Modifier.weight(1f), height = 64.dp, description = "${tile.label}: ${tile.value ?: ""}",
                            onLongClick = tileLongPress(tile) { optionsFor = id }, longClickLabel = tile.longPressLabel) { tile.action() }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().then(pullUp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications", color = g.textPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        GlassButton(if (clearArmed) "Select again: clear all" else "Clear notifications", { NotificationsListener.clearAll() },
                            icon = Icons.Rounded.DeleteSweep, enabled = NotificationModel.anyClearable(items),
                            kind = if (clearArmed) app.wayfinder.ui.ButtonKind.DANGER else app.wayfinder.ui.ButtonKind.NORMAL)
                    }
                }
                if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(g.textTertiary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Notifications, null, tint = g.textPrimary, modifier = Modifier.size(28.dp))
                        }
                        Text("No notifications", color = g.textPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(items, key = { it.key }) { n -> Card(ctx, n) }
            }
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp).size(40.dp, 5.dp).clip(CircleShape)   // Thomas: not stuck to the last card
                .background(g.textTertiary.copy(alpha = 0.5f)))
        }
        optionsFor?.let { tiles[it] }?.takeIf { it.options != null }?.let { TileOptionsSheet(it) { optionsFor = null } }
    }

    private fun nowWord(): String = android.content.res.Resources.getSystem().let { r ->
        r.getIdentifier("now_string_shortest", "string", "android").takeIf { it != 0 }?.let { runCatching { r.getString(it) }.getOrNull() }
    } ?: "now"

    @Composable
    private fun Card(ctx: Context, n: NotifItem) {
        val g = LocalGlass.current
        val (appName, icon) = remember(n.pkg) { appOf(ctx, n.pkg) }
        val now = System.currentTimeMillis()
        // under a minute: Android's own "now" (its shade's word, in the system's language), not "0 minutes ago"
        val age = if (now - n.postTime < DateUtils.MINUTE_IN_MILLIS) nowWord()
            else DateUtils.getRelativeTimeSpanString(n.postTime, now, DateUtils.MINUTE_IN_MILLIS).toString()
        var slide by remember { mutableFloatStateOf(0f) }
        val shape = RoundedCornerShape(20.dp)
        val gate = remember { app.wayfinder.ui.ReleaseGate() }
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        val open = { NotificationsListener.open(n.key); close() }
        Column(Modifier.fillMaxWidth()
            .graphicsLayer { translationX = slide; alpha = 1f - (kotlin.math.abs(slide) / size.width).coerceIn(0f, 1f) }
            .focusIndicator(shape)
            // the controller: A opens (on its release), X clears it (an ongoing one stays)
            .onKeyEvent { e ->
                if (e.key == androidx.compose.ui.input.key.Key.ButtonX) {
                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && n.clearable) {
                        // the next card takes the focus first (else it restarted at the first tile)
                        if (!focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)) focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                        NotificationsListener.dismiss(n.key)
                    }
                    true
                } else gate.onKey(e, open)
            }
            .glassSurface(g, shape, well = g.surfaceModal)
            .clickable { open() }
            .then(if (!n.clearable) Modifier else Modifier.pointerInput(n.key) {
                // swiped aside past 30 % of its width: cleared (an ongoing one doesn't move)
                detectHorizontalDragGestures(onDragEnd = { if (kotlin.math.abs(slide) > size.width * 0.3f) NotificationsListener.dismiss(n.key); slide = 0f },
                    onDragCancel = { slide = 0f }) { change, dx -> change.consume(); slide += dx }
            })
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (icon != null) Image(icon, null, Modifier.size(48.dp).clip(CircleShape))
                else Box(Modifier.size(48.dp).clip(CircleShape).background(g.textTertiary.copy(alpha = 0.15f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(appName, color = g.textPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(age, color = g.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    n.title?.let { Text(it, color = g.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    n.text?.let { Text(it, color = g.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (n.actions.isNotEmpty()) Row(Modifier.padding(start = 62.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in n.actions) GlassButton(a.title, { NotificationsListener.act(n.key, a.index); close() }, size = ButtonSize.COMPACT)
            }
        }
    }
}
