package app.wayfinder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wayfinder.ui.CapsuleShape
import app.wayfinder.ui.GlassTokens
import app.wayfinder.ui.LocalGlass
import app.wayfinder.ui.Radii
import app.wayfinder.ui.ThorGlassTheme
import app.wayfinder.ui.accentStops
import app.wayfinder.ui.glassSurface
import app.wayfinder.ui.iisuSlider
import app.wayfinder.ui.menuCard

/**
 * iiSU fork: the volume bar. The volume keys set the media volume themselves (no FLAG_SHOW_UI: Android's bar stays away)
 * and this one shows instead, on the controller's screen, at the right edge where Android's was: the ringer mode, the
 * volume track (tap or drag it), the speaker and the level, and "…" which widens it into a sound side panel (ringer mode
 * and the volume of both screens, top, bottom). It is drawn as iiSU's menus are: a solid card, its 20 dp corners, its
 * slider colour. It goes 1.5 s after the last press or touch (5 s when widened). Touch only, as Android's: a touch beside
 * it goes to the game, and the controller stays with the game.
 */
object VolumeBar {
    private const val HIDE_MS = 1500L
    private const val HIDE_WIDE_MS = 5000L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var view: android.view.View? = null
    private var wm: android.view.WindowManager? = null
    private var owner: app.wayfinder.keyboard.ComposeOwner? = null
    private var shownOn: Int? = null
    private var level by mutableIntStateOf(0)
    private var max by mutableIntStateOf(15)
    private var ringer by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
    private var wide by androidx.compose.runtime.mutableStateOf(false)
    private val hide = Runnable { fadeOut() }
    private val keys = VolumeRules.Keys()
    private var repeat: Runnable? = null
    // AudioManager.FLAG_SHOW_UI_WARNINGS (hidden): Android's headphone safe-volume prompt, without its bar. With no
    // flag, a raise past the safe level (10 of 15 on the Thor) is refused silently.
    private const val WARNINGS = 1 shl 10

    /** The accessibility service's key filter: true = consumed. An up goes where its down went ([VolumeRules.Keys]). */
    fun onVolumeKey(service: AccessibilityService, event: KeyEvent, display: Int): Boolean {
        val dir = VolumeRules.direction(event.keyCode)
        if (dir == 0) return false
        if (event.action == KeyEvent.ACTION_UP) { stopRepeat(); return keys.up(event.keyCode) }
        val audio = service.getSystemService(AudioManager::class.java)
        // screen off, a call: Android's own handling (in-call volume, its bar)
        val ours = service.getSystemService(android.os.PowerManager::class.java).isInteractive && audio.mode == AudioManager.MODE_NORMAL
        return when (keys.down(event.keyCode, event.repeatCount, android.os.SystemClock.uptimeMillis() - event.eventTime, ours)) {
            VolumeRules.Down.PASS -> false
            VolumeRules.Down.SWALLOW -> true
            VolumeRules.Down.STEP -> {
                val step = if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                adjust(service, step, display)
                // held: Android makes no repeats for a key the filter took, so the bar steps on as Android's would
                stopRepeat()
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                var steps = 0
                repeat = object : Runnable { override fun run() {
                    adjust(service, step, display)
                    // at an end, or past the whole range (a lost up): stop. Not "the level didn't move": on the Thor it is
                    // read back before the step lands, which stopped the repeat after one step
                    val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if ((if (dir > 0) v < max else v > 0) && ++steps < 2 * max)
                        main.postDelayed(this, android.view.ViewConfiguration.getKeyRepeatDelay().toLong())
                } }.also { main.postDelayed(it, android.view.ViewConfiguration.getKeyRepeatTimeout().toLong()) }
                true
            }
        }
    }

    private fun stopRepeat() { repeat?.let { main.removeCallbacks(it) }; repeat = null }

    /** Media volume one step [direction] (ADJUST_RAISE / ADJUST_LOWER), then the bar on [display] (Android's if it can't show). */
    fun adjust(ctx: Context, direction: Int, display: Int) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, WARNINGS)
        // posted: the key filter answers at once (Android waits 500 ms for it at most, then applies the key itself)
        main.post {
            val service = ctx as? AccessibilityService ?: ForegroundAppService.accessibility()
            if (service == null || !show(service, display))
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun close() { stopRepeat(); removeBar() }

    /** The window only: a held key's repeat goes on (a first press shows the bar after its repeat is set). */
    private fun removeBar() {
        main.removeCallbacks(hide)
        val v = view ?: return
        runCatching { wm?.removeViewImmediate(v) }
        owner?.destroy()
        view = null; wm = null; owner = null; shownOn = null; wide = false
    }

    private fun read(ctx: Context) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        ringer = audio.ringerMode
    }

    private fun bump() {
        main.removeCallbacks(hide); view?.animate()?.cancel(); view?.alpha = 1f
        main.postDelayed(hide, if (wide) HIDE_WIDE_MS else HIDE_MS)
    }

    private fun fadeOut() { view?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction { close() }?.start() }

    /** Shows or updates the bar on [display]; false if the window couldn't be added. */
    private fun show(service: AccessibilityService, display: Int): Boolean {
        read(service)
        if (view != null && shownOn == display) { bump(); return true }
        app.wayfinder.ui.IisuLookSource.refreshAsync(service)   // iiSU's theme, font, glass colour may have changed
        removeBar()   // one bar at a time: the controller moved to the other screen
        val d = service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(display) ?: return false
        val ctx = android.view.ContextThemeWrapper(service.createDisplayContext(d), R.style.Theme_Wayfinder_Glass)
        val wm = ctx.getSystemService(android.view.WindowManager::class.java)
        LinkedVolume.init(ctx)   // the wide form's per-screen sliders
        val owner = app.wayfinder.keyboard.ComposeOwner()
        val root = object : android.widget.FrameLayout(ctx) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean { bump(); return super.dispatchTouchEvent(ev) }
        }
        owner.attach(root)
        root.addView(androidx.compose.ui.platform.ComposeView(ctx).apply {
            setContent {
                ThorGlassTheme(dark = GlassTokens.isDark(ctx)) { app.wayfinder.ui.CappedFontScale {
                    Bar(
                        onRinger = {
                            val audio = ctx.getSystemService(AudioManager::class.java)
                            val dnd = ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted ||
                                PServiceBridge.isAvailable()   // cached by the look's read when the bar opened
                            setRinger(ctx, VolumeRules.nextRingerMode(audio.ringerMode, dnd))
                        },
                        onLevel = { v ->
                            ctx.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_MUSIC, v, WARNINGS)
                            read(ctx)
                        },
                        onRingerMode = { m -> setRinger(ctx, m) },
                        onMore = { wide = true; bump() },
                    )
                } }
            }
        })
        val lp = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            x = (16 * ctx.resources.displayMetrics.density).toInt()
            title = "WayfinderVolumeBar"
        }
        return try {
            wm.addView(root, lp)
            view = root; this.wm = wm; this.owner = owner; shownOn = display
            bump()
            true
        } catch (t: Throwable) {
            owner.destroy()
            android.util.Log.w("ThorVolumeBar", "addView: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * Ringer [mode]. Into or out of Silent, Android asks for Do Not Disturb access: granted once, on the first such
     * touch, through AYN's root service (the panel's Do Not Disturb switch already goes through it).
     */
    private fun setRinger(ctx: Context, mode: Int) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        val set = {
            runCatching { audio.ringerMode = mode }.onFailure { android.util.Log.w("ThorVolumeBar", "ringer: ${it.javaClass.simpleName}") }
            read(ctx)
        }
        if (!VolumeRules.needsDndAccess(audio.ringerMode, mode) ||
            ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted) { set(); return }
        Thread { PServiceBridge.exec("cmd notification allow_dnd ${ctx.packageName}"); main.post { set() } }
            .apply { isDaemon = true; name = "dnd-access" }.start()
    }

    @Composable
    private fun Bar(onRinger: () -> Unit, onLevel: (Int) -> Unit, onRingerMode: (Int) -> Unit, onMore: () -> Unit) {
        val g = LocalGlass.current
        // iiSU's menus: a solid card (no home-tile veil: it would vanish over a game), 20 dp corners
        val card = RoundedCornerShape(Radii.Modal)
        if (wide) { Wide(card, onRingerMode); return }
        Column(Modifier.padding(8.dp).width(68.dp).glassSurface(g, card, well = menuCard(g), veil = false).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).glassSurface(g, androidx.compose.foundation.shape.CircleShape, well = g.surfaceModal, veil = false)
                .clickable(onClick = onRinger).semantics { contentDescription = "Ringer: ${ringerName(ringer)}" },
                contentAlignment = Alignment.Center) {
                Icon(ringerIcon(ringer), null, tint = g.textPrimary, modifier = Modifier.size(22.dp))
            }
            val pct = VolumeRules.percent(level, max)
            // the track: iiSU's slider colour, filled from the bottom; a tap or a drag sets the level where the finger is
            Box(Modifier.width(40.dp).height(170.dp)
                .semantics { contentDescription = "Volume $pct %" }
                .pointerInput(max) {
                    fun at(y: Float) = Math.round((1f - (y / size.height).coerceIn(0f, 1f)) * max)
                    detectTapGestures { onLevel(at(it.y)) }
                }
                .pointerInput(max) {
                    fun at(y: Float) = Math.round((1f - (y / size.height).coerceIn(0f, 1f)) * max)
                    detectVerticalDragGestures { change, _ -> onLevel(at(change.position.y)) }
                }, contentAlignment = Alignment.BottomCenter) {
                // iiSU's slider, standing: filled from the bottom, a dot per step, the bar thumb across
                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                    iisuSlider(if (max > 0) level / max.toFloat() else 0f, max + 1, g.dark, 0f, size.height, vertical = true)
                }
            }
            Icon(when (VolumeRules.speaker(level, max)) {
                VolumeRules.Speaker.OFF -> Icons.Rounded.VolumeOff
                VolumeRules.Speaker.LOW -> Icons.Rounded.VolumeDown
                VolumeRules.Speaker.HIGH -> Icons.Rounded.VolumeUp
            }, null, tint = g.textPrimary, modifier = Modifier.size(22.dp))
            Text("$pct", color = g.textPrimary, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.MoreHoriz, "More sound", tint = g.textSecondary,
                modifier = Modifier.size(36.dp).clip(CapsuleShape).clickable(onClick = onMore).padding(6.dp))
        }
    }

    /** "…": the bar widened into a sound side panel at the same edge — the ringer mode and the volume of each screen. */
    @Composable
    private fun Wide(card: RoundedCornerShape, onRingerMode: (Int) -> Unit) {
        val g = LocalGlass.current
        Column(Modifier.padding(8.dp).width(360.dp).glassSurface(g, card, well = menuCard(g), veil = false).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Sound", color = g.textPrimary, style = MaterialTheme.typography.titleMedium)
            // the ringer mode: three tiles, icon and word; the chosen one in the selection gradient (no tick mark)
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (m in listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT))
                    app.wayfinder.ui.IisuToggleTile(ringerIcon(m), ringerLabel(m), ringer == m, Modifier.weight(1f),
                        description = "Ringer: ${ringerName(m)}") { onRingerMode(m) }
            }
            // the volumes, in iiSU's slider: flat fill, a bar for a thumb, big enough for a finger
            var top by androidx.compose.runtime.remember { mutableIntStateOf(LinkedVolume.top()) }
            var bottom by androidx.compose.runtime.remember { mutableIntStateOf(LinkedVolume.bottom()) }
            val changed = LinkedVolume.changes.intValue
            androidx.compose.runtime.LaunchedEffect(changed) { top = LinkedVolume.top(); bottom = LinkedVolume.bottom() }
            BigSlider("Both screens", top) { t -> LinkedVolume.setBoth(t); top = t; bottom = (t + LinkedVolume.offset).coerceIn(0, 15) }
            BigSlider("Top screen", top) { t -> LinkedVolume.setTopOnly(t); top = t }
            BigSlider("Bottom screen", bottom) { b -> LinkedVolume.setBottomOnly(b); bottom = b }
        }
    }

    /** A volume in iiSU's slider, sized for a finger: its name and value above. */
    @Composable
    private fun BigSlider(label: String, value: Int, onChange: (Int) -> Unit) {
        val g = LocalGlass.current
        val steps = 15
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                Text(label, color = g.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(volumeText(value), color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            // iiSU's slider (GlassSlider draws it): its dots, the bar thumb, ◂ ▸ to step
            app.wayfinder.ui.GlassSlider(value.coerceIn(0, steps) / steps.toFloat(),
                Modifier.semantics { contentDescription = "$label: ${volumeText(value)}" }, step = 1f / steps, height = 44.dp) { v ->
                onChange(Math.round(v * steps)); bump()
            }
        }
    }

    private fun ringerLabel(mode: Int) = when (mode) {
        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; AudioManager.RINGER_MODE_SILENT -> "Silent"; else -> "Ring"
    }

    private fun ringerIcon(mode: Int) = when (mode) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
        AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff
        else -> Icons.Rounded.NotificationsActive
    }

    private fun ringerName(mode: Int) = when (mode) {
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"; AudioManager.RINGER_MODE_SILENT -> "silent"; else -> "ring"
    }
}
