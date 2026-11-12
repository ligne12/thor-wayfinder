package app.wayfinder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * volume track (drag it), the speaker and the level, and "…" for the quick panel. It goes 1.5 s after the last press or
 * touch. Touch only, as Android's: a touch beside it goes to the game, and the controller stays with the game.
 */
object VolumeBar {
    private const val HIDE_MS = 1500L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var view: android.view.View? = null
    private var wm: android.view.WindowManager? = null
    private var owner: app.wayfinder.keyboard.ComposeOwner? = null
    private var shownOn: Int? = null
    private var level by mutableIntStateOf(0)
    private var max by mutableIntStateOf(15)
    private var ringer by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
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
        view = null; wm = null; owner = null; shownOn = null
    }

    private fun read(ctx: Context) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        ringer = audio.ringerMode
    }

    private fun bump() { main.removeCallbacks(hide); view?.animate()?.cancel(); view?.alpha = 1f; main.postDelayed(hide, HIDE_MS) }

    private fun fadeOut() { view?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction { close() }?.start() }

    /** Shows or updates the bar on [display]; false if the window couldn't be added. */
    private fun show(service: AccessibilityService, display: Int): Boolean {
        read(service)
        if (view != null && shownOn == display) { bump(); return true }
        close()   // one bar at a time: the controller moved to the other screen
        val d = service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(display) ?: return false
        val ctx = android.view.ContextThemeWrapper(service.createDisplayContext(d), R.style.Theme_Wayfinder_Glass)
        val wm = ctx.getSystemService(android.view.WindowManager::class.java)
        val owner = app.wayfinder.keyboard.ComposeOwner()
        val root = object : android.widget.FrameLayout(ctx) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean { bump(); return super.dispatchTouchEvent(ev) }
        }
        owner.attach(root)
        root.addView(androidx.compose.ui.platform.ComposeView(ctx).apply {
            setContent {
                ThorGlassTheme(dark = GlassTokens.isDark(ctx)) {
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
                        onMore = { close(); QuickPanelWindow.show(service, display) },
                    )
                }
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
    private fun Bar(onRinger: () -> Unit, onLevel: (Int) -> Unit, onMore: () -> Unit) {
        val g = LocalGlass.current
        val card = RoundedCornerShape(Radii.Card)
        // opaque under the glass, as Android's bar: a 13 % veil alone would vanish over a game
        Column(Modifier.padding(8.dp).width(64.dp).glassSurface(g, card, well = g.surfaceModal).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ringerIcon = when (ringer) {
                AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
                AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff
                else -> Icons.Rounded.NotificationsActive
            }
            val ringerName = when (ringer) { AudioManager.RINGER_MODE_VIBRATE -> "vibrate"; AudioManager.RINGER_MODE_SILENT -> "silent"; else -> "ring" }
            Box(Modifier.size(44.dp).glassSurface(g, RoundedCornerShape(Radii.Control)).clickable(onClick = onRinger)
                .semantics { contentDescription = "Ringer: $ringerName" }, contentAlignment = Alignment.Center) {
                Icon(ringerIcon, null, tint = g.textPrimary, modifier = Modifier.size(22.dp))
            }
            val pct = VolumeRules.percent(level, max)
            // the track: filled from the bottom; a tap or a drag sets the level where the finger is
            Box(Modifier.width(12.dp).height(160.dp).clip(CapsuleShape).background(g.textTertiary.copy(alpha = 0.22f))
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
                    .background(Brush.verticalGradient(g.accentStops.reversed()), CapsuleShape))
            }
            Icon(when (VolumeRules.speaker(level, max)) {
                VolumeRules.Speaker.OFF -> Icons.Rounded.VolumeOff
                VolumeRules.Speaker.LOW -> Icons.Rounded.VolumeDown
                VolumeRules.Speaker.HIGH -> Icons.Rounded.VolumeUp
            }, null, tint = g.textPrimary, modifier = Modifier.size(22.dp))
            Text("$pct", color = g.textPrimary, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.MoreHoriz, "More: quick panel", tint = g.textSecondary,
                modifier = Modifier.size(36.dp).clip(CapsuleShape).clickable(onClick = onMore).padding(6.dp))
        }
    }
}
