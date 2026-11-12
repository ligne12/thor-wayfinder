package app.wayfinder.ui

import app.wayfinder.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IisuLookTest {
    private fun sample(name: String) = javaClass.getResource("/iisu/$name")!!.readBytes()

    // A DataStore Preferences entry: {1: {1: key, 2: Value}} — enough of an encoder for the edge cases below.
    private fun entry(key: String, value: ByteArray): ByteArray {
        val k = key.toByteArray()
        val inner = byteArrayOf(0x0A, k.size.toByte()) + k + byteArrayOf(0x12, value.size.toByte()) + value
        return byteArrayOf(0x0A, inner.size.toByte()) + inner
    }
    private fun str(s: String) = byteArrayOf(0x2A, s.length.toByte()) + s.toByteArray()
    private fun float(f: Float) = java.nio.ByteBuffer.allocate(5).order(java.nio.ByteOrder.LITTLE_ENDIAN).put(0x15).putFloat(f).array()

    @Test fun parse_sampleLight_readsEveryField() {
        val look = IisuLookParser.parse(sample("sample-light.pb"))
        assertEquals(IisuLook(dark = false, focus = 0xFF699CA1.toInt(), glassOpacity = 0.45f, glassTint = 0xFF4D7CFE.toInt(),
            glassOutline = false, font = "cal_sans", ringTurns = true, customGradient = true, blur = true), look)
    }

    @Test fun parse_sampleDark_readsDark_andLeavesUnsetGlassNull() {
        val look = IisuLookParser.parse(sample("sample-dark.pb"))
        assertEquals(true, look.dark)
        assertEquals(0xFF74E3EE.toInt(), look.focus)
        assertEquals(null, look.glassOpacity)
        assertEquals(null, look.glassTint)
    }

    @Test fun parse_systemTheme_givesNullDark() =
        assertEquals(null, IisuLookParser.parse(entry("theme_mode", str("SYSTEM"))).dark)

    @Test fun parse_emptyOrBroken_givesDefaults() {
        val light = sample("sample-light.pb")
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0, 1, 2, 3), byteArrayOf(0x0A, 0x7F), "theme=".toByteArray(),
                             ByteArray(64) { 0xFF.toByte() }))
            assertEquals(IisuLook(), IisuLookParser.parse(bytes))
        IisuLookParser.parse(light.copyOf(light.size - 7))   // truncated mid-entry: must not throw
    }

    @Test fun parse_outOfRangeOrWrongType_isIgnoredFieldByField() {
        val look = IisuLookParser.parse(entry("home_glass_opacity", float(3f)) + entry("theme_mode", float(1f)) +
            entry("launcher_font_choice", str("cal_sans")))
        assertEquals(IisuLook(font = "cal_sans"), look)
    }

    @Test fun colours_ringIsThePrimary_accentTheGradientsMiddle() {
        val look = IisuLook(focus = 0xFF699CA1.toInt(), customGradient = true)
        val c = look.colours(dark = false)
        assertEquals(0xFF699CA1.toInt(), c.ring)
        assertEquals(look.selection().second, c.accent)
        assertEquals(IisuLook.ACCENT2, c.accent2)
        assertEquals(IisuLook.BRAND_CYAN, IisuLook().colours(dark = true).ring)
    }

    @Test fun glass_boxesAreIisusVeil_theTintColoursTheBackground() {
        // measured on iiSU's home: the empty tile is 13 % black whatever the opacity (45 % and 90 % gave the same pixels);
        // custom glass colour #4D7CFE turned the background from #F5F5F5 to #E4E8F2 (about 10 %)
        for (op in listOf(0.45f, 0.9f)) {
            val v = IisuLook(glassOpacity = op).glass(dark = false).panel
            assertEquals(0.13f, v.alpha, 0.01f); assertEquals(0f, v.red + v.green + v.blue, 0.01f)
        }
        val plain = IisuLook().glass(dark = false).base
        val tinted = IisuLook(glassTint = 0xFF4D7CFE.toInt()).glass(dark = false).base
        assertTrue("tinted toward blue", tinted.blue - tinted.red > plain.blue - plain.red + 0.05f)
        assertTrue("lightly", tinted.red > 0.80f)
        // switching the colour off keeps it with alpha 0 (seen on the Thor): no tint
        assertEquals(plain, IisuLook(glassTint = 0x004D7CFE).glass(dark = false).base)
    }

    private fun varint(v: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(); var x = v
        while (true) { val b = (x and 0x7F).toInt(); x = x ushr 7; if (x == 0L) { out.write(b); break } else out.write(b or 0x80) }
        return out.toByteArray()
    }
    private fun int(argb: Int) = byteArrayOf(0x18) + varint(argb.toLong())
    private fun bool(b: Boolean) = byteArrayOf(0x08, if (b) 1 else 0)
    private fun hue(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val mx = maxOf(r, g, b); val d = mx - minOf(r, g, b)
        val h = when (mx) { r -> ((g - b) / d) % 6; g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60
        return (h + 360) % 360
    }

    @Test fun parse_readsBothSelectionColours_andTheGradientSwitch() {
        val look = IisuLookParser.parse(entry("focus_indicator_secondary_color_argb", int(0xFFFF8A6E.toInt())) +
            entry("custom_selection_gradient_enabled", bool(false)))
        assertEquals(0xFFFF8A6E.toInt(), look.focusSecondary)
        assertEquals(false, look.customGradient)
    }

    @Test fun selection_customColours_goThroughTheHuesBetween() {
        val teal = 0xFF3FA8B2.toInt()
        val (from, mid, to) = IisuLook(focus = teal, focusSecondary = 0xFFC56EFF.toInt(), customGradient = true).selection()
        assertEquals(teal, from); assertEquals(0xFFC56EFF.toInt(), to)
        assertTrue("teal to violet passes by blue: ${hue(mid)}", hue(mid) in 220f..245f)
        val (_, mid2, _) = IisuLook(focus = teal, focusSecondary = 0xFFFF8A6E.toInt(), customGradient = true).selection()
        assertTrue("teal to salmon passes by green: ${hue(mid2)}", hue(mid2) in 80f..115f)
    }

    @Test fun selection_withoutCustomGradient_isIisusOwn() {
        val (from, _, to) = IisuLook(focus = 0xFF3FA8B2.toInt(), focusSecondary = 0xFFFF8A6E.toInt(), customGradient = false).selection()
        assertEquals(IisuLook.BRAND_CYAN, from); assertEquals(IisuLook.BRAND_VIOLET, to)
        // iiSU removes the key when the switch is off (seen on the Thor): absent means off
        val (from2, _, _) = IisuLook(focus = 0xFF3FA8B2.toInt(), customGradient = null).selection()
        assertEquals(IisuLook.BRAND_CYAN, from2)
    }

    @Test fun parse_glassBlur_isTheHomeGlassMode() {
        assertEquals(false, IisuLookParser.parse(entry("single_screen_home_glass_mode", str("Opaque"))).blur)
        assertEquals(true, IisuLookParser.parse(entry("single_screen_home_glass_mode", str("Blurred"))).blur)
        assertEquals(null, IisuLookParser.parse(entry("theme_mode", str("DARK"))).blur)   // absent: iiSU's default, blurred
    }

    @Test fun fontFamilyName_readsTheNameTable() {
        assertEquals("Cal Sans", IisuFonts.familyName(java.io.File("src/main/res/font/cal_sans.ttf").readBytes()))
        assertEquals(null, IisuFonts.familyName(ByteArray(40)))
    }

    private fun argb(c: androidx.compose.ui.graphics.Color): Long = TokenArgb.of(
        (Math.round(c.alpha * 255) shl 24) or (Math.round(c.red * 255) shl 16) or (Math.round(c.green * 255) shl 8) or Math.round(c.blue * 255))
    /** [top] (with its alpha) over the opaque [under]. */
    private fun over(top: androidx.compose.ui.graphics.Color, under: Long): Long =
        TokenArgb.mix(under, argb(top) or 0xFF000000, top.alpha.toDouble())

    @Test fun glass_textReadsOnTheBoxesAsDrawn() {
        for (dark in listOf(false, true)) {
            val g = IisuLook().glass(dark)
            val box = over(g.panel, argb(g.base))                    // the veil over the background: what is drawn
            assertTrue("secondary dark=$dark", TokenArgb.contrast(over(g.textSecondary, box), box) >= 4.5)
            assertTrue("tertiary dark=$dark", TokenArgb.contrast(over(g.textTertiary, box), box) >= 3.0)
        }
    }

    @Test fun glass_inkOnAccentReadsOnTheDrawnGradient_whateverTheSelectionColours() {
        val g = IisuLook(focus = 0xFF1E3A8A.toInt(), focusSecondary = 0xFF6B21A8.toInt(), customGradient = true).glass(dark = false)
        for (stop in g.accentStops) assertTrue("ink on $stop", TokenArgb.contrast(argb(g.onAccent), argb(stop) or 0xFF000000) >= 4.5)
    }

    @Test fun themeMode_followsIisu_orTheSystem() {
        assertEquals(ThemeMode.DARK, IisuLook(dark = true).themeMode())
        assertEquals(ThemeMode.LIGHT, IisuLook(dark = false).themeMode())
        assertEquals(ThemeMode.SYSTEM, IisuLook().themeMode())
    }

    @Test fun accent_extremes_keepReadableInk() {
        for (argb in listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFF74E3EE, 0xFF699CA1))
            assertTrue("ink on %08X".format(argb), TokenArgb.contrast(TokenArgb.onAccent(argb), argb) >= 4.5)
    }
}
