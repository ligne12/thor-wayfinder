package app.wayfinder

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationModelTest {
    @Test fun pullDown_needsADownwardSwipe() {
        assertEquals(true, NotificationModel.pullDown(dy = 120f, dx = 10f, minPx = 90f))
        assertEquals(false, NotificationModel.pullDown(dy = 40f, dx = 0f, minPx = 90f))    // too short: a tap or a nudge
        assertEquals(false, NotificationModel.pullDown(dy = 120f, dx = 200f, minPx = 90f)) // sideways
        assertEquals(false, NotificationModel.pullDown(dy = -150f, dx = 0f, minPx = 90f))  // upward
    }
}
