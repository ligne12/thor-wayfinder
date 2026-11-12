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
}
