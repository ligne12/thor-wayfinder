package app.wayfinder

import app.wayfinder.lights.LightMode
import app.wayfinder.lights.LightProfile

/** iiSU fork: the pure rules of the tiles' options windows ([TileOptions]), tested off the device. */
object TileOptionRules {
    /** Do not disturb's choices, in the window's order: Off, Priority only, Alarms only, Total silence — `cmd notification set_dnd`'s words. */
    val ZEN_CMD = listOf("off", "priority", "alarms", "none")

    /** Settings.Global zen_mode (0 off, 1 priority, 2 total silence, 3 alarms) → the window's choice; unknown: Off. */
    fun zenChoice(zenMode: Int): Int = when (zenMode) { 1 -> 1; 3 -> 2; 2 -> 3; else -> 0 }

    /** Night light's colour temperature range when the device doesn't say (AOSP's config_nightDisplayColorTemperatureMin/Max). */
    const val NIGHT_MIN = 2596
    const val NIGHT_MAX = 4082

    /** Night light's slider: [fraction] 0 (left, least warm: [max] K) to 1 (right, warmest: [min] K). */
    fun kelvinAt(fraction: Float, min: Int, max: Int): Int = Math.round(max - fraction.coerceIn(0f, 1f) * (max - min))

    /** The slider position of [kelvin], clamped to the range. */
    fun fractionOf(kelvin: Int, min: Int, max: Int): Float =
        if (max <= min) 0f else ((max - kelvin).toFloat() / (max - min)).coerceIn(0f, 1f)

    /** `cmd wifi status`: the network it is connected to, null when it isn't. */
    fun currentSsid(status: String): String? = Regex("Wifi is connected to \"(.*)\"").find(status)?.groupValues?.get(1)

    /** The saved networks (`cmd wifi list-networks`) seen by the last scan (`cmd wifi list-scan-results`), strongest first,
     *  each once (a network saves one entry per security type and a router shows one BSSID per band). */
    fun savedInRange(networks: String, scan: String): List<String> {
        val ws = Regex("\\s+")
        // "<id>  <ssid…>  <security>": the SSID is what lies between (it may hold spaces)
        val saved = networks.lines().filter { it.isNotBlank() && !it.startsWith("Network Id") }
            .mapNotNull { l -> l.trim().split(ws).takeIf { it.size >= 3 }?.let { it.subList(1, it.size - 1).joinToString(" ") } }.toSet()
        // "<bssid> <frequency> <rssi> <age> <ssid…> [flags]"
        val seen = HashMap<String, Int>()
        for (l in scan.lines()) {
            val t = l.trim().split(ws, limit = 5)
            if (t.size < 5 || ':' !in t[0]) continue
            val rssi = t[2].toIntOrNull() ?: continue
            val ssid = Regex("^(.*?)\\s*(\\[.*])?$").find(t[4])?.groupValues?.get(1)?.trim()?.split(ws)?.joinToString(" ")
            if (ssid.isNullOrEmpty()) continue   // a hidden network
            seen[ssid] = maxOf(seen[ssid] ?: Int.MIN_VALUE, rssi)
        }
        return seen.entries.filter { it.key in saved }.sortedByDescending { it.value }.map { it.key }
    }

    /** The stick lights in effect [mode]: from Off, the profile saved when they went off (else the default); colours,
     *  speed and brightness kept. */
    fun nextLightProfile(cur: LightProfile, saved: LightProfile?, mode: LightMode): LightProfile =
        (if (cur.mode == LightMode.OFF && mode != LightMode.OFF) saved ?: LightProfile() else cur).copy(mode = mode)
}
