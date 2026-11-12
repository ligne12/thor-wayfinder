package app.wayfinder.ui

/**
 * Reads iiSU's settings file (Jetpack DataStore Preferences, a protobuf: repeated field 1 = {1: key, 2: Value}).
 * Pure and total: any input gives an [IisuLook]; a field that is missing, out of range or of the wrong type stays null.
 * Format and field names: docs/superpowers/specs/iisu-settings-format.md.
 */
object IisuLookParser {
    fun parse(bytes: ByteArray): IisuLook = parseOrNull(bytes) ?: IisuLook()

    /** Null when [bytes] is not a preferences file (empty, cut short, something else) — the caller keeps its last look. */
    fun parseOrNull(bytes: ByteArray): IisuLook? {
        val prefs = runCatching { entries(bytes) }.getOrNull() ?: return null
        if (prefs.isEmpty()) return null
        return IisuLook(
            dark = when (prefs["theme_mode"] as? String) { "DARK" -> true; "LIGHT" -> false; else -> null },
            focus = (prefs["focus_indicator_primary_color_argb"] as? Long)?.toInt(),
            focusSecondary = (prefs["focus_indicator_secondary_color_argb"] as? Long)?.toInt(),
            customGradient = prefs["custom_selection_gradient_enabled"] as? Boolean,
            blur = when (prefs["single_screen_home_glass_mode"] as? String) { "Opaque" -> false; "Blurred" -> true; else -> null },
            glassOpacity = (prefs["home_glass_opacity"] as? Float)?.takeIf { it in 0f..1f },
            glassTint = (prefs["home_glass_tint_color_argb"] as? Long)?.toInt(),
            glassOutline = prefs["glass_outline_enabled"] as? Boolean,
            font = prefs["launcher_font_choice"] as? String,
            ringTurns = prefs["focus_ring_animations_enabled"] as? Boolean,
        )
    }

    private class Reader(val b: ByteArray, var i: Int = 0, val end: Int = b.size) {
        fun more() = i < end
        fun varint(): Long {
            var r = 0L; var shift = 0
            while (true) {
                val c = b[i++].toLong()                      // past the end: throws, caught in parseOrNull
                r = r or ((c and 0x7F) shl shift); shift += 7
                if (c >= 0) return r
                require(shift < 70)
            }
        }
        fun chunk(): Reader { val n = varint().toInt(); require(n >= 0 && i + n <= end); return Reader(b, i, i + n).also { i += n } }
        fun fixed32(): Int { require(i + 4 <= end); var v = 0; for (k in 0..3) v = v or ((b[i++].toInt() and 0xFF) shl (8 * k)); return v }
        fun text() = String(b, i, end - i, Charsets.UTF_8)
        fun skip(wire: Int) { when (wire) { 0 -> varint(); 1 -> { require(i + 8 <= end); i += 8 }; 2 -> chunk(); 5 -> fixed32(); else -> error("wire $wire") } }
    }

    private fun entries(bytes: ByteArray): Map<String, Any> {
        val out = HashMap<String, Any>()
        val top = Reader(bytes)
        while (top.more()) {
            val tag = top.varint().toInt()
            require(tag == 0x0A)                              // a preferences file has nothing but field 1 at the top
            val e = top.chunk(); var key: String? = null; var value: Any? = null
            while (e.more()) {
                val t = e.varint().toInt()
                when (t) { 0x0A -> key = e.chunk().text(); 0x12 -> value = value(e.chunk()); else -> e.skip(t and 7) }
            }
            if (key != null && value != null) out[key] = value
        }
        return out
    }

    private fun value(v: Reader): Any? {
        var out: Any? = null
        while (v.more()) {
            val t = v.varint().toInt()
            when (t) {
                0x08 -> out = v.varint() != 0L                       // boolean
                0x15 -> out = Float.fromBits(v.fixed32())            // float
                0x18, 0x20 -> out = v.varint()                       // integer, long
                0x2A -> out = v.chunk().text()                       // string
                else -> v.skip(t and 7)
            }
        }
        return out
    }
}
