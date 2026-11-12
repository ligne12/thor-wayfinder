package app.wayfinder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * iiSU's appearance (Settings › Appearance › Style and Glass), the source of Wayfinder's look in this fork.
 * A null field means "not read": the default applies. Format: docs/superpowers/specs/iisu-settings-format.md.
 */
data class IisuLook(
    val dark: Boolean? = null,        // null: iiSU follows the system, or nothing was read
    val focus: Int? = null,           // ARGB: iiSU's focus indicator colour (Style)
    val glassOpacity: Float? = null,  // 0..1
    val glassTint: Int? = null,       // ARGB, alpha = strength
    val glassOutline: Boolean? = null,
    val font: String? = null,
    val ringTurns: Boolean? = null,   // Motion: the focus ring's gradient turns (iiSU's focus ring animation)
) {
    /** The four colours the theme builder takes (glassColorsFor's "your own colours" path), for the light or dark look. */
    fun colours(dark: Boolean): CustomColours {
        val opacity = (glassOpacity ?: if (dark) 0.60f else 0.72f).coerceIn(0f, 1f)
        // dark: measured on iiSU's dark settings and home (page #252525 to #2D2F34, cards #2B2C31)
        val plain = if (dark) 0xFF2E2F34L else 0xFFFFFFFFL
        // the tint's alpha is its strength in iiSU (0 = untinted glass)
        val tint = glassTint?.let { TokenArgb.mix(plain, TokenArgb.of(it), (it ushr 24) / 255.0).toInt() } ?: plain.toInt()
        return CustomColours(
            bg = if (dark) 0xFF262628.toInt() else 0xFFF2F2F2.toInt(),
            card = (tint and 0x00FFFFFF) or (Math.round(opacity * 255) shl 24),
            text = if (dark) 0xFFF7F8FA.toInt() else 0xFF4E4A56.toInt(),
            accent = ACCENT, accent2 = ACCENT2,
            ring = (focus ?: FOCUS_DEFAULT) or 0xFF000000.toInt(),
            outline = true,   // iiSU draws its rim on every card and tile, whatever its glass outline setting (home only)
        )
    }

    companion object {
        // iiSU's own controls (switch track, selected text), measured on its settings: sky #6CACE2 → blue #6E84DF → violet
        // #9675E3. Not a setting in iiSU, so fixed here.
        const val SKY = 0xFF6CACE2.toInt()
        const val ACCENT = 0xFF6E84DF.toInt()
        const val ACCENT2 = 0xFF9675E3.toInt()
        /** The focus colour when iiSU has none stored: its selected-text blue-violet. */
        const val FOCUS_DEFAULT = 0xFF7472D5.toInt()
    }

    /** Wayfinder's theme setting for this look: iiSU's light or dark, else the system's. */
    fun themeMode(): app.wayfinder.ThemeMode = when (dark) {
        true -> app.wayfinder.ThemeMode.DARK; false -> app.wayfinder.ThemeMode.LIGHT; null -> app.wayfinder.ThemeMode.SYSTEM
    }
}

/** The last look read from iiSU; [IisuLook] defaults until then. Compose state: an open window follows a new read. */
object IisuLookSource {
    var current by mutableStateOf(IisuLook())
        private set

    private const val FILE = "/data/data/com.iisulauncher/files/datastore/launcher_settings.preferences_pb"
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastStamp = ""

    /**
     * Re-reads iiSU's settings if the file changed, off the main thread ([app.wayfinder.PServiceBridge.exec] blocks), read
     * only. Nothing readable (no root service, no file, not a preferences file): the last look stays.
     */
    private fun log(what: String) { if (app.wayfinder.BuildConfig.DEBUG) android.util.Log.d("IisuLook", what) }

    fun refreshAsync() {
        if (!busy.compareAndSet(false, true)) return
        Thread {
            try {
                if (!app.wayfinder.PServiceBridge.isAvailable()) { log("no root service"); return@Thread }
                val stamp = app.wayfinder.PServiceBridge.exec("stat -c %Y-%s $FILE")?.trim().orEmpty()
                if (stamp.isEmpty() || stamp == lastStamp) { log("stamp '$stamp' (last '$lastStamp')"); return@Thread }
                val b64 = app.wayfinder.PServiceBridge.exec("base64 -w 0 $FILE") ?: return@Thread   // -w 0: the service returns the first line only
                log("read ${b64.length} chars")
                val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(b64.trim()) }.getOrNull() ?: return@Thread
                val look = IisuLookParser.parseOrNull(bytes) ?: run { log("not a preferences file (${bytes.size} bytes)"); return@Thread }
                log("look $look")
                lastStamp = stamp
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    current = look
                    app.wayfinder.AppSettings.setMode(look.themeMode())
                }
            } finally { busy.set(false) }
        }.apply { isDaemon = true; name = "iisu-look" }.start()
    }
}
