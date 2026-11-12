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

    private fun n(key: String, at: Long, group: String? = null, summary: Boolean = false, clearable: Boolean = true) =
        NotifItem(key, "app.x", at, "t", "x", clearable, group, summary, emptyList())

    @Test fun visible_hidesASummaryWithItsChildren_newestFirst() {
        val items = listOf(n("a", 10, group = "g", summary = true), n("b", 20, group = "g"), n("c", 30), n("d", 5, group = "h", summary = true))
        assertEquals(listOf("c", "b", "d"), NotificationModel.visible(items).map { it.key })   // "d": a summary alone stays
    }

    @Test fun anyClearable_ignoresOngoing() {
        assertEquals(false, NotificationModel.anyClearable(listOf(n("a", 1, clearable = false))))
        assertEquals(true, NotificationModel.anyClearable(listOf(n("a", 1, clearable = false), n("b", 2))))
    }
}
