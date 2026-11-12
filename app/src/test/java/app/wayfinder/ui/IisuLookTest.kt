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
        assertEquals(IisuLook(dark = false, accent = 0xFF699CA1.toInt(), glassOpacity = 0.45f, glassTint = 0xFF4D7CFE.toInt(),
            glassOutline = false, font = "cal_sans"), look)
    }

    @Test fun parse_sampleDark_readsDark_andLeavesUnsetGlassNull() {
        val look = IisuLookParser.parse(sample("sample-dark.pb"))
        assertEquals(true, look.dark)
        assertEquals(0xFF74E3EE.toInt(), look.accent)
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

    @Test fun glassTint_alphaIsItsStrength() {
        fun rgb(look: IisuLook) = look.colours(dark = false).card and 0x00FFFFFF
        assertEquals(0xFFFFFF, rgb(IisuLook(glassTint = 0x004D7CFE)))                 // strength 0: iiSU's untinted glass
        assertEquals(0x4D7CFE, rgb(IisuLook(glassTint = 0xFF4D7CFE.toInt())))         // full strength
        assertEquals(0xFFFFFF, rgb(IisuLook()))
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
