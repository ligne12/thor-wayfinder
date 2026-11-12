package app.wayfinder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.wayfinder.ui.CappedFontScale
import app.wayfinder.ui.GlassButton
import app.wayfinder.ui.GlassScreen
import app.wayfinder.ui.GlassTokens
import app.wayfinder.ui.LocalGlass
import app.wayfinder.ui.Radii
import app.wayfinder.ui.ThorGlassTheme
import app.wayfinder.ui.focusIndicator
import app.wayfinder.ui.glassSurface

/**
 * iiSU fork: the multitask. Android's Recents (launcher3 Quickstep) can't be restyled, so this covers it as soon as its window
 * shows: the apps in iiSU's look ([RecentsSource]'s list, the oldest on the left), each card with its last picture, the screen
 * it is on (1 top, 2 bottom: tap to send it to the other) and a padlock ("Clear all" and "Close apps" leave it open). Tap a card
 * to open it, drag it up to close it; Screenshot, Clear all; a tap beside the cards, or Back, returns to the app left.
 * Android's Recents stays underneath: when it goes (Home, a swipe), this goes too ([ForegroundAppService]). Main thread;
 * root calls on their own threads.
 */
object RecentsScreen {
    private const val FADE_MS = 150L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var view: android.view.View? = null
    private var wm: WindowManager? = null
    private var owner: app.wayfinder.keyboard.ComposeOwner? = null
    private var closing = false
    private var service: AccessibilityService? = null
    private var origin: String? = null
    private var always: Set<String> = emptySet()
    private var otherScreenApps: () -> Collection<String> = { emptyList() }
    /** The screen it shows on; null: not shown. */
    var shownOn: Int? = null
        private set
    private var selectedId by mutableIntStateOf(-1)
    private var removed by mutableStateOf<Set<Int>>(emptySet())   // closed cards, gone at once (before the list is read again)

    private val cards: List<RecentTask> get() = RecentsModel.order(RecentsSource.tasks.orEmpty()).filter { it.taskId !in removed }
    private fun selected(): RecentTask? = cards.firstOrNull { it.taskId == selectedId }
    private fun bg(work: () -> Unit) = Thread(work).apply { isDaemon = true; name = "recents-act" }.start()

    /** Shows it on [display] over Android's Recents; false if the window couldn't be added (Android's stays). */
    fun show(service: AccessibilityService, display: Int, origin: String?, always: Set<String>, otherScreenApps: () -> Collection<String>): Boolean {
        view?.let { v ->
            if (shownOn == display) {
                if (closing) { closing = false; v.animate().cancel(); v.animate().alpha(1f).setDuration(FADE_MS).start() }
                return true
            }
            remove()   // one at a time: Recents opened on the other screen
        }
        val d = service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(display) ?: return false
        val ctx = android.view.ContextThemeWrapper(service.createDisplayContext(d), R.style.Theme_Wayfinder_Glass)
        val wm = ctx.getSystemService(WindowManager::class.java)
        this.service = service; this.origin = origin; this.always = always; this.otherScreenApps = otherScreenApps
        removed = emptySet()
        cards.let { selectedId = it.getOrNull(RecentsModel.initial(it, origin))?.taskId ?: -1 }
        app.wayfinder.ui.IisuLookSource.refreshAsync(service)   // iiSU's theme, font, colours may have changed
        val owner = app.wayfinder.keyboard.ComposeOwner()
        val root = object : android.widget.FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                when (event.keyCode) {
                    // the D-pad (a HAT made into arrows by this window): left / right browse; up / down would take the focus to
                    // a badge or a button while A still opens the chosen card
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.action == KeyEvent.ACTION_DOWN) browse(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                }
                if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP) leave()
                return true
            }
        }
        owner.attach(root)
        root.addView(androidx.compose.ui.platform.ComposeView(ctx).apply {
            setContent { ThorGlassTheme(dark = GlassTokens.isDark(ctx)) { CappedFontScale { Screen(ctx, display) } } }
        })
        root.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}
            override fun onViewDetachedFromWindow(v: android.view.View) { if (view === root) remove() }   // removed by the system
        })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // focusable: iiSU's ring turns only in a focused window, and Android's Back comes here
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { title = "WayfinderRecents" }
        root.alpha = 0f
        return try {
            wm.addView(root, lp)
            view = root; this.wm = wm; this.owner = owner; shownOn = display; closing = false
            root.animate().alpha(1f).setDuration(FADE_MS).start()
            true
        } catch (t: Throwable) {
            owner.destroy()
            android.util.Log.w("ThorRecents", "addView: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** Fades it out (Recents left, an app opened); [now]: at once (the screen going off — no animation runs then). */
    fun close(now: Boolean = false) {
        if (now) { remove(); return }
        val v = view ?: return
        if (closing) return
        closing = true
        v.animate().alpha(0f).setDuration(FADE_MS).withEndAction { if (view === v && closing) remove() }.start()
    }

    private fun remove() {
        val v = view ?: return
        view = null; shownOn = null; closing = false
        v.animate().cancel()
        runCatching { wm?.removeViewImmediate(v) }
        owner?.destroy()
        wm = null; owner = null; service = null
    }

    /** A tap beside the cards, or Back: the app left, else Android's own Back on Recents (sent once this has faded). */
    fun leave() {
        if (back()) return
        val s = service
        close()
        main.postDelayed({ s?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }, FADE_MS + 50)
    }

    /** Opens the app left (the card Recents was opened from); false: none in the list. */
    fun back(): Boolean {
        val t = cards.lastOrNull { it.pkg == origin } ?: return false
        selectedId = t.taskId
        openSelected()
        return true
    }

    /** The next card [dir] (-1 older, on the left; +1 newer), stopping at the ends. */
    fun browse(dir: Int) {
        val c = cards
        if (c.isEmpty()) return
        val i = c.indexOfFirst { it.taskId == selectedId }.let { if (it < 0) c.lastIndex else it }
        selectedId = c[(i + dir).coerceIn(0, c.lastIndex)].taskId
    }

    fun openSelected() {
        val t = selected() ?: return
        val s = service ?: return
        val here = shownOn ?: return
        val left = cards.lastOrNull { it.pkg == origin && it.display == here && it.taskId != t.taskId }
        bg {
            val ok = RecentsSource.open(s, t)
            // an app on the other screen comes forward there; this screen leaves Recents for the app it had (or its home)
            if (ok && t.display != here) { if (left == null || !RecentsSource.open(s, left)) PServiceBridge.goHomeOnDisplay(here) }
            // failed (gone, no root): Android's Recents underneath takes over
            main.post { if (!ok) android.util.Log.w("ThorRecents", "open ${t.pkg}: failed"); close() }
        }
    }

    fun closeSelected() {
        val t = selected() ?: return
        val here = shownOn ?: return
        val i = cards.indexOf(t)
        removed = removed + t.taskId
        val rest = cards
        selectedId = rest.getOrNull(i.coerceAtMost(rest.lastIndex))?.taskId ?: -1
        RecentsSource.forget(t.taskId)
        bg { PServiceBridge.exec("am stack remove ${t.taskId}") }
        if (rest.isEmpty()) { bg { PServiceBridge.goHomeOnDisplay(here) }; close() }   // the last card: its home
    }

    fun clearAll() {
        val s = service ?: return
        val here = shownOn ?: return
        val keep = RecentsModel.clearKeep(AppSettings.recentsLocked, otherScreenApps(), always).joinToString(",")
        bg {
            PServiceBridge.runEntryPoint(s, "RecentsTool", keep); PServiceBridge.goHomeOnDisplay(here)
            main.post { RecentsSource.refreshAsync(s) }   // the next open starts from what is left
        }
        close()
    }

    private fun move(t: RecentTask) {
        val s = service ?: return
        val to = ForegroundAppService.otherScreen(t.display) ?: return
        bg { PServiceBridge.moveTaskViaBinder(s, t.pkg, to, t.display); main.post { RecentsSource.refreshAsync(s) } }
    }

    private fun screenshot() {
        val t = selected() ?: return
        val s = service ?: return
        val here = shownOn ?: return
        bg {
            val ok = RecentsSource.saveScreenshot(s, t)
            main.post { Overlays.pill(if (ok) "Screenshot saved" else "No screenshot for this app", CueOrigin.Screen(here)) }
        }
    }

    /** A picture, halved when wider than 960 px (the carousel's cards are smaller than that). */
    private fun decode(f: java.io.File?): ImageBitmap? {
        f ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = if (bounds.outWidth > 960) 2 else 1 })
            ?.asImageBitmap()
    }

    /** A card's shape: its screen's (the picture is of that screen), 16:9 if unknown. */
    private fun aspect(ctx: Context, display: Int): Float =
        ScreenSize.real(ctx, display)?.takeIf { it.y > 0 }?.let { it.x.toFloat() / it.y } ?: (16f / 9f)

    @Composable
    private fun Screen(ctx: Context, display: Int) {
        val list = cards
        LaunchedEffect(list) {
            if (list.isEmpty()) { close(); return@LaunchedEffect }   // nothing left to show: Android's own empty Recents
            if (list.none { it.taskId == selectedId }) selectedId = list.getOrNull(RecentsModel.initial(list, origin))?.taskId ?: -1
        }
        val ratios = remember { HashMap<Int, Float>() }
        GlassScreen(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { leave() } }) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cardH = maxHeight * 0.6f
                val half = maxWidth / 2
                fun width(t: RecentTask): Dp = cardH * ratios.getOrPut(t.display) { aspect(ctx, t.display) }
                val state = rememberLazyListState()
                val density = LocalDensity.current
                val idx = list.indexOfFirst { it.taskId == selectedId }
                // the chosen card to the centre: the row's padding is half the screen, so its start sits at the centre. A frame
                // later than the card's focus, whose bring-into-view scroll would cancel it; at once the first time
                val first = remember { booleanArrayOf(true) }
                LaunchedEffect(selectedId, list.size, list.getOrNull(idx)?.display) {   // its screen sets its width
                    if (idx < 0) return@LaunchedEffect
                    androidx.compose.runtime.withFrameNanos { }
                    val off = with(density) { (width(list[idx]) / 2).roundToPx() }
                    if (first[0]) { first[0] = false; state.scrollToItem(idx, off) } else state.animateScrollToItem(idx, off)
                }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    LazyRow(Modifier.fillMaxWidth(), state = state, contentPadding = PaddingValues(horizontal = half),
                        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                        items(list, key = { it.taskId }) { t -> Card(ctx, t, width(t), cardH, t.taskId == selectedId) }
                    }
                    Spacer(Modifier.height(28.dp))
                    // the memory in use, read again every second (closing apps frees it a moment later)
                    val ram by produceState("") {
                        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
                        val info = android.app.ActivityManager.MemoryInfo()
                        while (true) { am.getMemoryInfo(info); value = RecentsModel.ramText(info.totalMem, info.availMem); kotlinx.coroutines.delay(1000) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ram, color = LocalGlass.current.textSecondary, style = MaterialTheme.typography.labelLarge)
                        GlassButton("Screenshot", { screenshot() })
                        GlassButton("Clear all", { clearAll() })
                    }
                }
            }
        }
    }

    @Composable
    private fun Card(ctx: Context, t: RecentTask, w: Dp, h: Dp, chosen: Boolean) {
        val g = LocalGlass.current
        val shape = RoundedCornerShape(Radii.Card)
        val pm = ctx.packageManager
        val label = remember(t.pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(t.pkg, 0)).toString() }.getOrDefault(t.pkg) }
        val icon = remember(t.pkg) { runCatching { pm.getApplicationIcon(t.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
        // decoded again with each read: the file is the task's latest picture (the app just left gets its new one a moment in)
        val pic by produceState<ImageBitmap?>(null, t.taskId, RecentsSource.reads) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decode(RecentsSource.shotFile(ctx, t.taskId)) }
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(chosen) { if (chosen) runCatching { focus.requestFocus() } }
        var lift by remember { mutableFloatStateOf(0f) }
        val hPx = with(LocalDensity.current) { h.toPx() }
        val locked = t.pkg in AppSettings.recentsLocked
        val screen = if (t.display == 0) "1" else "2"
        Column(Modifier.width(w).graphicsLayer { translationY = lift; alpha = 1f - (-lift / hPx).coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.let { Image(it, null, Modifier.size(28.dp)) }
                Text(label, color = g.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // the screen it is on: a tap sends it to the other
                Box(Modifier.size(36.dp).glassSurface(g, CircleShape, well = g.surfaceModal, veil = false).clickable { move(t) }
                    .semantics { contentDescription = "On screen $screen: move to the other screen" }, contentAlignment = Alignment.Center) {
                    Text(screen, color = g.textPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (locked) "Unlock $label" else "Lock $label",
                    tint = if (locked) g.accent else g.textSecondary,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { AppSettings.toggleRecentsLock(t.pkg) }.padding(6.dp))
            }
            Box(Modifier.size(w, h)
                .focusIndicator(shape)
                .onFocusChanged { if (it.isFocused) selectedId = t.taskId }
                .focusRequester(focus)
                .focusable()
                .clip(shape)
                .pointerInput(t.taskId) { detectTapGestures { selectedId = t.taskId; openSelected() } }
                .pointerInput(t.taskId) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (-lift > hPx * 0.25f) { selectedId = t.taskId; closeSelected() }; lift = 0f },
                        onDragCancel = { lift = 0f },
                    ) { change, dy -> change.consume(); lift = (lift + dy).coerceAtMost(0f) }
                }
                .semantics { contentDescription = label },
                contentAlignment = Alignment.Center) {
                val p = pic
                if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().glassSurface(g, shape), contentAlignment = Alignment.Center) {
                    icon?.let { Image(it, null, Modifier.size(96.dp)) }
                }
            }
        }
    }
}
