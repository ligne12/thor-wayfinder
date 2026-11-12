package app.wayfinder.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * iiSU's appearance (Settings › Appearance › Style and Glass), the source of Wayfinder's look in this fork.
 * A null field means "not read": the default applies. Format: docs/superpowers/specs/iisu-settings-format.md.
 */
data class IisuLook(
    val dark: Boolean? = null,        // null: iiSU follows the system, or nothing was read
    val focus: Int? = null,           // ARGB: the selection gradient's primary colour (Style)
    val focusSecondary: Int? = null,  // ARGB: its secondary colour
    val customGradient: Boolean? = null, // Style › Custom selection gradient (absent = off): off is iiSU's own cyan-to-violet
    val glassOpacity: Float? = null,  // 0..1
    val glassTint: Int? = null,       // ARGB: Glass › Custom glass colour; alpha 0 (or absent) while it is off
    val glassOutline: Boolean? = null,
    val font: String? = null,
    val ringTurns: Boolean? = null,   // Motion: the focus ring's gradient turns (iiSU's focus ring animation)
    val blur: Boolean? = null,        // Glass › Glass Blur (absent = on; off writes the home glass mode "Opaque")
) {
    /** The four colours the theme builder takes (glassColorsFor's "your own colours" path), for the light or dark look. */
    fun colours(dark: Boolean): CustomColours {
        // Glass › Custom glass colour (on: the colour with alpha FF; off: alpha 0, or no key): a light tint over iiSU's surfaces — about 10 %
        // on the home background (#F5F5F5 → #E4E8F2 with #4D7CFE), a little more on its menu cards (#DCE1F0). Its opacity
        // only weighs on menus: with the colour off it is greyed in iiSU.
        val tinted = glassTint != null && (glassTint ushr 24) != 0
        val opacity = ((if (tinted) glassOpacity else null) ?: if (dark) 0.60f else 0.72f).coerceIn(0f, 1f)
        // dark: measured on iiSU's dark settings and home (page #252525 to #2D2F34, cards #2B2C31)
        val plain = if (dark) 0xFF2E2F34L else 0xFFFFFFFFL
        val bg = if (dark) 0xFF262628L else 0xFFF2F2F2L
        fun tint(c: Long, t: Double) = if (!tinted) c.toInt() else TokenArgb.mix(c, TokenArgb.of(glassTint!!), t).toInt()
        return CustomColours(
            bg = tint(bg, 0.10),
            card = (tint(plain, 0.13) and 0x00FFFFFF) or (Math.round(opacity * 255) shl 24),
            text = if (dark) 0xFFF7F8FA.toInt() else 0xFF4E4A56.toInt(),
            accent = selection().second, accent2 = ACCENT2,   // iiSU's selected text: the selection gradient's middle
            ring = selection().first,
            outline = true,   // iiSU draws its rim on every card and tile, whatever its glass outline setting (home only)
        )
    }

    companion object {
        // iiSU's own controls (switch track, selected text), measured on its settings: sky #6CACE2 → blue #6E84DF → violet
        // #9675E3. Not a setting in iiSU, so fixed here.
        const val SKY = 0xFF6CACE2.toInt()
        const val ACCENT = 0xFF6E84DF.toInt()
        const val ACCENT2 = 0xFF9675E3.toInt()
        /** iiSU's slider fill (its settings' sliders, measured): one flat light cyan, not the gradient. */
        const val SLIDER = 0xFF72C5E4.toInt()
        /** iiSU's own selection gradient (its brand cyan and violet, read in its code): with the custom one off, or unset. */
        const val BRAND_CYAN = 0xFF71E0FF.toInt()
        const val BRAND_VIOLET = 0xFFC56EFF.toInt()

        /** [a] → [b] at [t] the way iiSU blends its selection colours: through the hues between (teal to salmon passes green). */
        fun hueMix(a: Int, b: Int, t: Float): Int {
            val x = hsv(a); val y = hsv(b)
            var dh = y[0] - x[0]
            if (dh > 180f) dh -= 360f else if (dh < -180f) dh += 360f
            return argb((x[0] + dh * t + 360f) % 360f, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t)
        }
        private fun hsv(c: Int): FloatArray {
            val r = (c shr 16 and 0xFF) / 255f; val g = (c shr 8 and 0xFF) / 255f; val b = (c and 0xFF) / 255f
            val mx = maxOf(r, g, b); val d = mx - minOf(r, g, b)
            val h = if (d == 0f) 0f else when (mx) { r -> ((g - b) / d + 6f) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
            return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
        }
        private fun argb(h: Float, s: Float, v: Float): Int {
            val c = v * s; val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1)); val m = v - c
            val (r, g, b) = when ((h / 60f).toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
            fun ch(f: Float) = Math.round((f + m) * 255f).coerceIn(0, 255)
            return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }

    /** The selection gradient: (primary, the hue between, secondary) — iiSU's Style colours, or its own when custom is off. */
    fun selection(): Triple<Int, Int, Int> {
        val custom = customGradient == true   // iiSU removes the key when the switch is off
        val from = (if (custom) focus else null)?.let { it or 0xFF000000.toInt() } ?: BRAND_CYAN
        val to = (if (custom) focusSecondary else null)?.let { it or 0xFF000000.toInt() } ?: BRAND_VIOLET
        return Triple(from, hueMix(from, to, 0.5f), to)
    }

    /**
     * The theme for this look. Cards and tiles take the look of iiSU's empty home tiles: 13 % black over the backdrop
     * (measured on both themes, the same at 45 % and 90 % glass opacity — the dots show through); a glass colour tints
     * the background under them ([colours]), not the veil.
     */
    fun glass(dark: Boolean): GlassColors {
        val base = glassColorsFor(dark, false, colours(dark))
        val veil = Color.Black.copy(alpha = 0.13f)
        val fill = Brush.verticalGradient(listOf(veil, veil))
        val (from, mid, to) = selection()
        // text measured against the box as drawn (the veil over the background), not the card colour the builder knew
        val box = TokenArgb.mix(TokenArgb.of(base.base.toArgbInt()), 0xFF000000, veil.alpha.toDouble())
        val text = TokenArgb.of(base.textPrimary.toArgbInt())
        // ink on the accent fills as drawn — the fixed gradient's stops (the accent itself only tints icons and words)
        val fills = listOf(SKY, ACCENT, ACCENT2).map { TokenArgb.of(it) }
        val ink = listOf(0xFF0A0C14, 0xFFFFFFFF).maxBy { c -> fills.minOf { TokenArgb.contrast(c, it) } }
        return base.copy(panelFill = fill, panelFillFocused = fill, panel = veil, ringStops = listOf(Color(to), Color(mid), Color(from)),
            textSecondary = base.textPrimary.copy(alpha = TokenArgb.minAlpha(text, box, 4.5, 0.82).toFloat()),
            textTertiary = base.textPrimary.copy(alpha = TokenArgb.minAlpha(text, box, 3.0, 0.58).toFloat()),
            onAccent = Color(ink.toInt()))
    }

    private fun Color.toArgbInt(): Int =
        (Math.round(alpha * 255) shl 24) or (Math.round(red * 255) shl 16) or (Math.round(green * 255) shl 8) or Math.round(blue * 255)

    /** Wayfinder's theme setting for this look: iiSU's light or dark, else the system's. */
    fun themeMode(): app.wayfinder.ThemeMode = when (dark) {
        true -> app.wayfinder.ThemeMode.DARK; false -> app.wayfinder.ThemeMode.LIGHT; null -> app.wayfinder.ThemeMode.SYSTEM
    }
}

/** The last look read from iiSU; [IisuLook] defaults until then. Compose state: an open window follows a new read. */
object IisuLookSource {
    var current by mutableStateOf(IisuLook())
        private set
    /** iiSU's chosen font when it isn't Cal Sans ([IisuFonts]); null: Cal Sans. */
    var fontFamily by mutableStateOf<androidx.compose.ui.text.font.FontFamily?>(null)
        private set
    @Volatile private var fontFor: String? = "cal_sans"
    @Volatile private var fontLoaded: androidx.compose.ui.text.font.FontFamily? = null

    private const val FILE = "/data/data/com.iisulauncher/files/datastore/launcher_settings.preferences_pb"
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var lastStamp = ""

    /**
     * Re-reads iiSU's settings if the file changed, off the main thread ([app.wayfinder.PServiceBridge.exec] blocks), read
     * only. Nothing readable (no root service, no file, not a preferences file): the last look stays.
     */
    // one line per read at most (a changed file), so kept in release: it is how to tell what Wayfinder took from iiSU
    private fun log(what: String) = android.util.Log.i("IisuLook", what)

    fun refreshAsync(ctx: android.content.Context) {
        val appCtx = ctx.applicationContext
        if (!busy.compareAndSet(false, true)) return
        Thread {
            try {
                // nothing readable (no root service, no iiSU): the theme follows the system — the Hub has no theme choice
                val toSystem = { android.os.Handler(android.os.Looper.getMainLooper()).post { app.wayfinder.AppSettings.setMode(app.wayfinder.ThemeMode.SYSTEM) } }
                if (!app.wayfinder.PServiceBridge.isAvailable()) { log("no root service"); toSystem(); return@Thread }
                // the inode too: DataStore replaces the file, and a colour change keeps its size
                val stamp = app.wayfinder.PServiceBridge.exec("stat -c %Y-%s-%i $FILE")?.trim().orEmpty()
                if (stamp.isEmpty()) { log("no iiSU settings"); lastStamp = ""; toSystem(); return@Thread }
                if (stamp == lastStamp) return@Thread
                val b64 = app.wayfinder.PServiceBridge.exec("base64 -w 0 $FILE") ?: return@Thread   // -w 0: the service returns the first line only
                log("read ${b64.length} chars")
                val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(b64.trim()) }.getOrNull() ?: return@Thread
                val look = IisuLookParser.parseOrNull(bytes) ?: run { log("not a preferences file (${bytes.size} bytes)"); return@Thread }
                log("look $look")
                lastStamp = stamp
                val family = if (look.font == fontFor) fontLoaded else IisuFonts.load(appCtx, look.font)
                // a font iiSU has that couldn't be read now is tried again on the next read
                if (family != null || look.font == null || look.font == "cal_sans") { fontFor = look.font; fontLoaded = family }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    current = look
                    fontFamily = family
                    app.wayfinder.AppSettings.setMode(look.themeMode())
                }
            } finally { busy.set(false) }
        }.apply { isDaemon = true; name = "iisu-look" }.start()
    }
}
