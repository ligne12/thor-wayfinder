package app.wayfinder

import app.wayfinder.lights.LightMode
import app.wayfinder.lights.LightProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class TileOptionRulesTest {
    // zen_mode: 0 off, 1 priority, 2 total silence, 3 alarms → the window's order: Off, Priority only, Alarms only, Total silence
    @Test fun zenChoice_mapsZenModeToTheWindowsOrder() {
        assertEquals(0, TileOptionRules.zenChoice(0)); assertEquals(1, TileOptionRules.zenChoice(1))
        assertEquals(2, TileOptionRules.zenChoice(3)); assertEquals(3, TileOptionRules.zenChoice(2))
        assertEquals(0, TileOptionRules.zenChoice(9))
    }

    @Test fun zenCmd_followsTheWindowsOrder() =
        assertEquals(listOf("off", "priority", "alarms", "none"), TileOptionRules.ZEN_CMD)

    @Test fun lightMode_fromOff_startsFromTheSavedProfile() {
        val saved = LightProfile(mode = LightMode.STATIC, left = 1, right = 2)
        assertEquals(saved.copy(mode = LightMode.SPECTRUM),
            TileOptionRules.nextLightProfile(LightProfile(mode = LightMode.OFF), saved, LightMode.SPECTRUM))
    }

    @Test fun lightMode_fromOffWithNothingSaved_startsFromTheDefault() =
        assertEquals(LightProfile().copy(mode = LightMode.BREATHING),
            TileOptionRules.nextLightProfile(LightProfile(mode = LightMode.OFF), null, LightMode.BREATHING))

    @Test fun lightMode_keepsColoursAndBrightness() {
        val cur = LightProfile(mode = LightMode.STATIC, left = 5, right = 6, brightness = 0.4f)
        assertEquals(cur.copy(mode = LightMode.STROBE), TileOptionRules.nextLightProfile(cur, null, LightMode.STROBE))
    }

    @Test fun lightMode_toOff_keepsTheRest() {
        val cur = LightProfile(mode = LightMode.STATIC, left = 5, right = 6)
        assertEquals(cur.copy(mode = LightMode.OFF), TileOptionRules.nextLightProfile(cur, null, LightMode.OFF))
    }
}
