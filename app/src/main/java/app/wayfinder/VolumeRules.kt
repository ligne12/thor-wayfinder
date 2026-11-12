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

    /** +1 for Volume up (KEYCODE_VOLUME_UP 24), -1 for Volume down (25), 0 for any other key. */
    fun direction(keyCode: Int): Int = when (keyCode) { 24 -> 1; 25 -> -1; else -> 0 }
}
