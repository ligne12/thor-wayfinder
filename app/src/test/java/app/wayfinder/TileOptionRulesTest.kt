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

    // Night light: right (1f) is warmer, so fewer kelvins
    @Test fun nightLight_rightIsWarmer() {
        assertEquals(4082, TileOptionRules.kelvinAt(0f, 2596, 4082))
        assertEquals(2596, TileOptionRules.kelvinAt(1f, 2596, 4082))
        assertEquals(3339, TileOptionRules.kelvinAt(0.5f, 2596, 4082))
    }

    @Test fun nightLight_roundTrips() =
        assertEquals(0.25f, TileOptionRules.fractionOf(TileOptionRules.kelvinAt(0.25f, 2596, 4082), 2596, 4082), 0.01f)

    @Test fun nightLight_clampsOutOfRange() {
        assertEquals(0f, TileOptionRules.fractionOf(9000, 2596, 4082), 0f)
        assertEquals(1f, TileOptionRules.fractionOf(1000, 2596, 4082), 0f)
    }

    // `cmd wifi` outputs: status / list-networks as the Thor prints them, list-scan-results in AOSP's format (names anonymized)
    private fun wifi(name: String) = javaClass.getResource("/wifi/$name.txt")!!.readText()

    @Test fun currentSsid_readsTheConnectedNetwork() = assertEquals("Home 5G", TileOptionRules.currentSsid(wifi("status")))

    @Test fun currentSsid_nullWhenNotConnected() {
        assertEquals(null, TileOptionRules.currentSsid("Wifi is disabled\n"))
        assertEquals(null, TileOptionRules.currentSsid("Wifi is enabled\nWifi is not connected\n"))
    }

    @Test fun savedInRange_savedAndScanned_strongestFirst_once() =
        assertEquals(listOf("Home 5G", "Office", "Cafe"), TileOptionRules.savedInRange(wifi("networks"), wifi("scan")))

    @Test fun savedInRange_noScanResults() =
        assertEquals(emptyList<String>(), TileOptionRules.savedInRange(wifi("networks"), "No scan results\n"))

    @Test fun lightMode_toOff_keepsTheRest() {
        val cur = LightProfile(mode = LightMode.STATIC, left = 5, right = 6)
        assertEquals(cur.copy(mode = LightMode.OFF), TileOptionRules.nextLightProfile(cur, null, LightMode.OFF))
    }
}
