package app.wayfinder

import app.wayfinder.lights.LightMode
import app.wayfinder.lights.LightProfile

/** iiSU fork: the pure rules of the tiles' options windows ([TileOptions]), tested off the device. */
object TileOptionRules {
    /** Do not disturb's choices, in the window's order: Off, Priority only, Alarms only, Total silence — `cmd notification set_dnd`'s words. */
    val ZEN_CMD = listOf("off", "priority", "alarms", "none")

    /** Settings.Global zen_mode (0 off, 1 priority, 2 total silence, 3 alarms) → the window's choice; unknown: Off. */
    fun zenChoice(zenMode: Int): Int = when (zenMode) { 1 -> 1; 3 -> 2; 2 -> 3; else -> 0 }

    /** The stick lights in effect [mode]: from Off, the profile saved when they went off (else the default); colours,
     *  speed and brightness kept. */
    fun nextLightProfile(cur: LightProfile, saved: LightProfile?, mode: LightMode): LightProfile =
        (if (cur.mode == LightMode.OFF && mode != LightMode.OFF) saved ?: LightProfile() else cur).copy(mode = mode)
}
