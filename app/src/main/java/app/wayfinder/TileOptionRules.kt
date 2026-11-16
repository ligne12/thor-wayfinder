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

    /** The stick lights in effect [mode]: from Off, the profile saved when they went off (else the default); colours,
     *  speed and brightness kept. */
    fun nextLightProfile(cur: LightProfile, saved: LightProfile?, mode: LightMode): LightProfile =
        (if (cur.mode == LightMode.OFF && mode != LightMode.OFF) saved ?: LightProfile() else cur).copy(mode = mode)
}
