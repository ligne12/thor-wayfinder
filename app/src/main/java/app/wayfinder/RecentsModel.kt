package app.wayfinder

/** iiSU fork: one app in the multitask carousel ([RecentsScreen]), as [RecentsTool]'s list mode reads it. */
data class RecentTask(val taskId: Int, val pkg: String, val display: Int, val lastActive: Long)

/** iiSU fork: the multitask's rules, pure so they are tested off the device. */
object RecentsModel {
    private val PKG = Regex("[A-Za-z0-9_.]+")

    /**
     * [RecentsTool]'s `tasks.txt`: `taskId \t package \t display \t lastActiveTime \t activityType` per line. Kept: the
     * standard tasks (type 1) and those whose type couldn't be read (-1); the home (2) and Recents itself (3) are not cards.
     */
    fun parse(lines: List<String>): List<RecentTask> = lines.mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 5 || !PKG.matches(f[1])) return@mapNotNull null
        val id = f[0].toIntOrNull(); val d = f[2].toIntOrNull(); val at = f[3].toLongOrNull(); val type = f[4].toIntOrNull()
        if (id == null || d == null || at == null || (type != 1 && type != -1)) null else RecentTask(id, f[1], d, at)
    }

    /** The carousel's order, Android's: the oldest on the left. */
    fun order(tasks: List<RecentTask>): List<RecentTask> = tasks.sortedWith(compareBy({ it.lastActive }, { it.taskId }))

    /** The card chosen first: the newest of the app left ([origin]), else the newest card; -1 with no card. */
    fun initial(ordered: List<RecentTask>, origin: String?): Int =
        ordered.indexOfLast { it.pkg == origin }.takeIf { it >= 0 } ?: ordered.lastIndex

    /** The memory in use, for an idea of what closing apps would free (Thomas's ask): `RAM 5.7 / 16.0 GB` (10^9 bytes). */
    fun ramText(total: Long, available: Long): String =
        String.format(java.util.Locale.ROOT, "RAM %.1f / %.1f GB", (total - available).coerceAtLeast(0) / 1e9, total.coerceAtLeast(0) / 1e9)

    /** What "Clear all" keeps: the locked apps, the app shown on the other screen, and [always] (the homes, Wayfinder). */
    fun clearKeep(locked: Set<String>, otherScreenApp: String?, always: Set<String>): Set<String> =
        locked + always + listOfNotNull(otherScreenApp)
}
