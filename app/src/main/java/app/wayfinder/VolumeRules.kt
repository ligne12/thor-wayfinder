package app.wayfinder

/** iiSU fork: the volume bar's rules ([VolumeBar]), pure so they are tested off the device. */
object VolumeRules {
    // AudioManager.RINGER_MODE_SILENT / VIBRATE / NORMAL
    private const val SILENT = 0
    private const val VIBRATE = 1
    private const val NORMAL = 2

    fun percent(level: Int, max: Int): Int = if (max <= 0) 0 else Math.round(100f * level.coerceIn(0, max) / max)

    enum class Speaker { OFF, LOW, HIGH }
    fun speaker(level: Int, max: Int): Speaker = when {
        level <= 0 || max <= 0 -> Speaker.OFF
        level * 2 < max -> Speaker.LOW
        else -> Speaker.HIGH
    }

    /** Android's order: ring, vibrate, silent. Silent needs Do Not Disturb access; without it, ring and vibrate only. */
    fun nextRingerMode(mode: Int, dndAccess: Boolean): Int = when (mode) {
        NORMAL -> VIBRATE
        VIBRATE -> if (dndAccess) SILENT else NORMAL
        else -> NORMAL
    }

    /** Android 13 lets an app into or out of Silent only with Do Not Disturb access (it toggles DND). */
    fun needsDndAccess(from: Int, to: Int): Boolean = from == SILENT || to == SILENT

    /** +1 for Volume up (KEYCODE_VOLUME_UP 24), -1 for Volume down (25), 0 for any other key. */
    fun direction(keyCode: Int): Int = when (keyCode) { 24 -> 1; 25 -> -1; else -> 0 }

    enum class Down { STEP, SWALLOW, PASS }

    /**
     * Which key downs the bar took, so each up goes where its down went (a call can start mid-press). Android makes no
     * repeats for a down the accessibility filter consumed, so the bar repeats a held key itself and swallows any the
     * firmware sends. A down older than [LATE_MS] reached Android already (the filter waits 500 ms): it stays Android's.
     */
    class Keys {
        private val held = mutableSetOf<Int>()
        fun down(keyCode: Int, repeatCount: Int, ageMs: Long, ours: Boolean): Down = when {
            repeatCount > 0 -> if (keyCode in held) Down.SWALLOW else Down.PASS
            !ours || ageMs > LATE_MS -> { held.remove(keyCode); Down.PASS }
            else -> { held.add(keyCode); Down.STEP }
        }
        fun up(keyCode: Int): Boolean = held.remove(keyCode)
    }
    const val LATE_MS = 400L
}
