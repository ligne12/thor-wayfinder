package app.wayfinder

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsModelTest {
    @Test fun parse_keepsStandardAndUnknownTasks() {
        val t = RecentsModel.parse(listOf(
            "1201\tcom.android.chrome\t4\t971314107\t1",
            "1174\tcom.iisulauncher\t0\t971314107\t2",      // home
            "1157\tcom.android.launcher3\t0\t1\t3",          // Recents itself
            "13\tapp.x\t0\t5\t-1",                           // type unread: kept
            "garbage", "12\tbad pkg!\t0\t5\t1", "x\tapp.y\t0\t5\t1"))
        assertEquals(listOf(RecentTask(1201, "com.android.chrome", 4, 971314107), RecentTask(13, "app.x", 0, 5)), t)
    }

    private fun t(id: Int, pkg: String, at: Long, d: Int = 0) = RecentTask(id, pkg, d, at)

    @Test fun order_oldestFirst_tieByTaskId() {
        assertEquals(listOf(3, 1, 2), RecentsModel.order(listOf(t(1, "a", 20), t(2, "b", 30), t(3, "c", 10))).map { it.taskId })
        assertEquals(listOf(1, 2), RecentsModel.order(listOf(t(2, "b", 5), t(1, "a", 5))).map { it.taskId })
    }

    @Test fun initial_originElseNewest() {
        val o = listOf(t(1, "a", 1), t(2, "b", 2), t(3, "a", 3), t(4, "c", 4))
        assertEquals(2, RecentsModel.initial(o, "a"))      // the newest task of the app left
        assertEquals(3, RecentsModel.initial(o, null))     // from home: the newest
        assertEquals(3, RecentsModel.initial(o, "zz"))     // not listed: the newest
        assertEquals(-1, RecentsModel.initial(emptyList(), "a"))
    }

    @Test fun ramText_usedOfTotal_inGigabytes() {
        // the Thor Max: MemTotal 15621188 kB, MemAvailable 10075500 kB
        assertEquals("RAM 5.7 / 16.0 GB", RecentsModel.ramText(total = 15_996_096_512L, available = 10_317_312_000L))
        assertEquals("RAM 0.0 / 8.0 GB", RecentsModel.ramText(total = 8_000_000_000L, available = 8_000_000_000L))
        assertEquals("RAM 0.0 / 0.0 GB", RecentsModel.ramText(total = 0L, available = 5L))   // never negative
    }

    @Test fun clearKeep_keepsLockedOtherScreenAndAlways() {
        assertEquals(setOf("lock.a", "other.b", "home.c", "app.wayfinder"),
            RecentsModel.clearKeep(setOf("lock.a"), listOf("other.b"), setOf("home.c", "app.wayfinder")))
        assertEquals(setOf("home.c"), RecentsModel.clearKeep(emptySet(), emptyList(), setOf("home.c")))
    }

    @Test fun clearKeep_keepsEveryAppSeenOnTheOtherScreen() {
        // the app tracked there and the one its window shows (tracking can lag): a game's unsaved run must survive
        assertEquals(setOf("tracked.a", "shown.b", "home.c"),
            RecentsModel.clearKeep(emptySet(), listOf("tracked.a", "shown.b"), setOf("home.c")))
    }
}
