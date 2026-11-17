package app.wayfinder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A held A on a panel tile: its options after [ReleaseGate.LONG_MS], never the short action as well. */
class ReleaseGateTest {
    @Test fun release_beforeHold_runsTheShortAction() {
        val g = ReleaseGate(); var short = 0
        g.press(0); g.release(canceled = false) { short++ }
        assertEquals(1, short)
    }

    @Test fun held_runsTheLongAction_andTheReleaseDoesNothing() {
        val g = ReleaseGate(); var short = 0; var long = 0
        g.press(0); g.held { long++ }; g.release(canceled = false) { short++ }
        assertEquals(1, long); assertEquals(0, short)
    }

    @Test fun held_withANoOp_swallowsTheRelease() {          // a tile of list C
        val g = ReleaseGate(); var short = 0
        g.press(0); g.held { }; g.release(canceled = false) { short++ }
        assertEquals(0, short)
    }

    @Test fun held_afterCancelledRelease_doesNothing() {
        val g = ReleaseGate(); var short = 0; var long = 0
        g.press(0); g.release(canceled = true) { short++ }; g.held { long++ }
        assertEquals(0, short); assertEquals(0, long)
    }

    @Test fun longPressTimeout_isHalfASecond() = assertEquals(500L, ReleaseGate.LONG_MS)
}
