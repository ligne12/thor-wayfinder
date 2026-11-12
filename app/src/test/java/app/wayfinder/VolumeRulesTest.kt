package app.wayfinder

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRulesTest {
    // AudioManager.RINGER_MODE_* and KeyEvent codes, copied: the unit-test JVM has neither class
    private val SILENT = 0; private val VIBRATE = 1; private val NORMAL = 2

    @Test fun percent_roundsAndClamps() {
        assertEquals(0, VolumeRules.percent(0, 15)); assertEquals(53, VolumeRules.percent(8, 15)); assertEquals(100, VolumeRules.percent(15, 15))
        assertEquals(0, VolumeRules.percent(3, 0)); assertEquals(100, VolumeRules.percent(20, 15))
    }

    @Test fun speaker_offLowHigh() {
        assertEquals(VolumeRules.Speaker.OFF, VolumeRules.speaker(0, 15))
        assertEquals(VolumeRules.Speaker.LOW, VolumeRules.speaker(7, 15))
        assertEquals(VolumeRules.Speaker.HIGH, VolumeRules.speaker(8, 15))
    }

    @Test fun nextRingerMode_cyclesNormalVibrateSilent() {
        assertEquals(VIBRATE, VolumeRules.nextRingerMode(NORMAL, dndAccess = true))
        assertEquals(SILENT, VolumeRules.nextRingerMode(VIBRATE, dndAccess = true))
        assertEquals(NORMAL, VolumeRules.nextRingerMode(SILENT, dndAccess = true))
    }

    @Test fun nextRingerMode_withoutDnd_skipsSilent() {
        assertEquals(NORMAL, VolumeRules.nextRingerMode(VIBRATE, dndAccess = false))
    }

    @Test fun direction_volumeKeysOnly() {
        assertEquals(1, VolumeRules.direction(24)); assertEquals(-1, VolumeRules.direction(25)); assertEquals(0, VolumeRules.direction(96))
    }

    @Test fun needsDndAccess_intoOrOutOfSilent() {
        assertEquals(true, VolumeRules.needsDndAccess(VIBRATE, SILENT)); assertEquals(true, VolumeRules.needsDndAccess(SILENT, NORMAL))
        assertEquals(false, VolumeRules.needsDndAccess(NORMAL, VIBRATE))
    }

    @Test fun keys_upFollowsItsDown() {
        val k = VolumeRules.Keys()
        assertEquals(VolumeRules.Down.STEP, k.down(24, repeatCount = 0, ageMs = 5, ours = true))
        assertEquals(true, k.up(24))
        assertEquals(false, k.up(24))   // an up with no consumed down is Android's
    }

    @Test fun keys_passedDownPassesItsUp_evenIfTheModeChanged() {
        val k = VolumeRules.Keys()
        assertEquals(VolumeRules.Down.PASS, k.down(25, repeatCount = 0, ageMs = 5, ours = false))   // a call began
        assertEquals(false, k.up(25))
    }

    @Test fun keys_lateDownIsAndroids() {
        val k = VolumeRules.Keys()
        // past the filter's timeout Android has already applied it: stepping again would count it twice
        assertEquals(VolumeRules.Down.PASS, k.down(24, repeatCount = 0, ageMs = 450, ours = true))
        assertEquals(false, k.up(24))
    }

    @Test fun keys_repeatsOfAConsumedDownAreSwallowed() {
        val k = VolumeRules.Keys()
        k.down(24, repeatCount = 0, ageMs = 5, ours = true)
        assertEquals(VolumeRules.Down.SWALLOW, k.down(24, repeatCount = 3, ageMs = 5, ours = true))   // the bar repeats it itself
        assertEquals(VolumeRules.Down.PASS, k.down(25, repeatCount = 3, ageMs = 5, ours = true))
    }
}
