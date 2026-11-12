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

    /** The accessibility service's key filter: true = consumed (the down and its up). */
    fun onVolumeKey(service: AccessibilityService, event: KeyEvent, display: Int): Boolean {
        val dir = VolumeRules.direction(event.keyCode)
        if (dir == 0) return false
        val audio = service.getSystemService(AudioManager::class.java)
        // screen off, a call: Android's own handling (in-call volume, its bar)
        if (!service.getSystemService(android.os.PowerManager::class.java).isInteractive || audio.mode != AudioManager.MODE_NORMAL) return false
        if (event.action == KeyEvent.ACTION_DOWN) adjust(service, if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, display)
        return true
    }

    /** Media volume one step [direction] (ADJUST_RAISE / ADJUST_LOWER), then the bar on [display] (Android's if it can't show). */
    fun adjust(ctx: Context, direction: Int, display: Int) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val service = ctx as? AccessibilityService ?: ForegroundAppService.accessibility()
        if (service == null || !show(service, display))
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    fun close() {
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
        app.wayfinder.ui.IisuLookSource.refreshAsync(service)   // iiSU's theme, font, glass colour may have changed
        if (view != null && shownOn == display) { bump(); return true }
        close()   // one bar at a time: the controller moved to the other screen
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
                            val dnd = ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
                            runCatching { audio.ringerMode = VolumeRules.nextRingerMode(audio.ringerMode, dnd) }
                            read(ctx)
                        },
                        onLevel = { v ->
                            ctx.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                            read(ctx)
                        },
                        onRingerMode = { m ->
                            runCatching { ctx.getSystemService(AudioManager::class.java).ringerMode = m }
                            read(ctx)
                        },
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

    @Composable
    private fun Bar(onRinger: () -> Unit, onLevel: (Int) -> Unit, onRingerMode: (Int) -> Unit, onMore: () -> Unit) {
        val g = LocalGlass.current
        // iiSU's menus: a solid card (no home-tile veil: it would vanish over a game), 20 dp corners
        val card = RoundedCornerShape(Radii.Modal)
        if (wide) { Wide(card, onRingerMode); return }
        Column(Modifier.padding(8.dp).width(68.dp).glassSurface(g, card, well = g.surfaceModal, veil = false).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).glassSurface(g, androidx.compose.foundation.shape.CircleShape, well = g.surfaceModal, veil = false)
                .clickable(onClick = onRinger).semantics { contentDescription = "Ringer: ${ringerName(ringer)}" },
                contentAlignment = Alignment.Center) {
                Icon(ringerIcon(ringer), null, tint = g.textPrimary, modifier = Modifier.size(22.dp))
            }
            val pct = VolumeRules.percent(level, max)
            // the track: iiSU's slider colour, filled from the bottom; a tap or a drag sets the level where the finger is
            Box(Modifier.width(14.dp).height(160.dp).clip(CapsuleShape).background(g.textTertiary.copy(alpha = 0.18f))
                .semantics { contentDescription = "Volume $pct %" }
                .pointerInput(max) {
                    fun at(y: Float) = Math.round((1f - (y / size.height).coerceIn(0f, 1f)) * max)
                    detectTapGestures { onLevel(at(it.y)) }
                }
                .pointerInput(max) {
                    fun at(y: Float) = Math.round((1f - (y / size.height).coerceIn(0f, 1f)) * max)
                    detectVerticalDragGestures { change, _ -> onLevel(at(change.position.y)) }
                }, contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(if (max > 0) level / max.toFloat() else 0f)
                    .background(androidx.compose.ui.graphics.Color(app.wayfinder.ui.IisuLook.SLIDER), CapsuleShape))
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
        Column(Modifier.padding(8.dp).width(360.dp).glassSurface(g, card, well = g.surfaceModal, veil = false).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Sound", color = g.textPrimary, style = MaterialTheme.typography.titleMedium)
            // the ringer mode: three tiles, icon and word; the chosen one in the selection gradient (no tick mark)
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (m in listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)) {
                    val on = ringer == m
                    val tile = RoundedCornerShape(16.dp)
                    Column(Modifier.weight(1f).height(72.dp)
                        .then(if (on) Modifier.clip(tile).background(Brush.linearGradient(g.accentStops), tile)
                              else Modifier.glassSurface(g, tile, well = g.surfaceModal, veil = false))
                        .clickable { onRingerMode(m) }
                        .semantics { contentDescription = "Ringer: ${ringerName(m)}" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val ink = if (on) g.onAccent else g.textPrimary
                        Icon(ringerIcon(m), null, tint = ink, modifier = Modifier.size(24.dp))
                        Text(ringerLabel(m), color = ink, style = MaterialTheme.typography.labelLarge)
                    }
                }
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

    /** iiSU's slider, sized for a finger: its name and value above, a 28 dp track filled in iiSU's slider colour, a bar thumb. */
    @Composable
    private fun BigSlider(label: String, value: Int, onChange: (Int) -> Unit) {
        val g = LocalGlass.current
        val steps = 15
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                Text(label, color = g.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(volumeText(value), color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)
                .semantics { contentDescription = "$label: ${volumeText(value)}" }
                .pointerInput(Unit) {
                    fun at(x: Float) = Math.round((x / size.width).coerceIn(0f, 1f) * steps)
                    detectTapGestures { onChange(at(it.x)); bump() }
                }
                .pointerInput(Unit) {
                    fun at(x: Float) = Math.round((x / size.width).coerceIn(0f, 1f) * steps)
                    detectHorizontalDragGestures { change, _ -> onChange(at(change.position.x)); bump() }
                }, contentAlignment = Alignment.CenterStart) {
                val f = value.coerceIn(0, steps) / steps.toFloat()
                Box(Modifier.fillMaxWidth().height(28.dp).clip(CapsuleShape).background(g.textTertiary.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(androidx.compose.ui.graphics.Color(app.wayfinder.ui.IisuLook.SLIDER)))
                }
                // the thumb: a white bar a little taller than the track, as iiSU's
                Box(Modifier.padding(start = (maxWidth - 8.dp) * f).width(8.dp).fillMaxHeight()
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(4.dp))
                    .border(1.dp, g.textTertiary.copy(alpha = 0.35f), RoundedCornerShape(4.dp)))
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
