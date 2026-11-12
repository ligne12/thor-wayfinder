package app.wayfinder

import android.accessibilityservice.AccessibilityService
import android.view.MotionEvent
import android.view.WindowManager

/**
 * iiSU fork: the swipe down from the top opens Wayfinder's notification center, not Android's shade. Android's shade is
 * kept from expanding (`cmd statusbar send-disable-flag statusbar-expansion`, root) and a 20 dp strip, invisible, along
 * the top of the top screen (the only one with a shade) takes the swipe. [stop] gives Android its shade back — every way
 * Wayfinder goes (service destroyed, turned off, the Hub's switch) calls it. No root: nothing is blocked, no strip.
 */
object NotificationGesture {
    private const val TAG = "ThorNotifs"
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    // one root command after the other, in order: a stop right after a start must end with the shade given back
    private val root = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var strip: android.view.View? = null
    private var wm: WindowManager? = null
    private var gen = 0

    /**
     * Blocks Android's shade and lays the strip; [onPull] runs (main thread) on a pull down from it. No root yet (the root
     * service can come up after Wayfinder at boot): tried again every 10 s for a minute.
     */
    fun start(service: AccessibilityService, attempt: Int = 0, onPull: () -> Unit) {
        val mine = ++gen
        root.execute {
            val ok = PServiceBridge.isAvailable() && PServiceBridge.exec("cmd statusbar send-disable-flag statusbar-expansion") != null
            main.post {
                if (mine != gen) return@post   // stopped meanwhile: its own "none" follows this command
                if (ok) addStrip(service, onPull)
                else {
                    android.util.Log.w(TAG, "no root (attempt $attempt): Android's shade stays")
                    if (attempt < 6) main.postDelayed({ if (mine == gen) start(service, attempt + 1, onPull) }, 10_000)
                }
            }
        }
    }

    /** Takes the strip away and gives Android its shade back. */
    fun stop() {
        gen++
        removeStrip()
        root.execute { PServiceBridge.exec("cmd statusbar send-disable-flag none") }
    }

    private fun addStrip(service: AccessibilityService, onPull: () -> Unit) {
        // a fresh strip each time: one left by an earlier service instance (a rebind) may already be gone from the screen
        removeStrip()
        val d = service.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0) ?: return
        val ctx = service.createDisplayContext(d)
        val wm = ctx.getSystemService(WindowManager::class.java)
        val minPx = 40 * ctx.resources.displayMetrics.density
        val v = object : android.view.View(ctx) {
            private var x0 = 0f; private var y0 = 0f; private var fired = false
            // the finger stays this window's after it leaves the strip: the whole pull is read here
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; fired = false }
                    MotionEvent.ACTION_MOVE -> if (!fired && NotificationModel.pullDown(e.rawY - y0, e.rawX - x0, minPx)) {
                        fired = true
                        android.util.Log.d(TAG, "pull")
                        onPull()
                    }
                }
                return true
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (20 * ctx.resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "WayfinderPullDown"
        }
        runCatching { wm.addView(v, lp); strip = v; this.wm = wm }
            .onFailure { android.util.Log.w(TAG, "strip: ${it.javaClass.simpleName}"); stop() }   // no strip: Android's shade back
    }

    private fun removeStrip() {
        val v = strip ?: return
        runCatching { wm?.removeViewImmediate(v) }
        strip = null; wm = null
    }
}
