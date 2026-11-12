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
}
