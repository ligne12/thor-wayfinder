package app.wayfinder

import android.app.ActivityManager
import android.os.IBinder

/**
 * Entry point run in a bare VM via `app_process` as root (uid 0), the same way
 * as [RootMover]. Clears background app tasks the way the Recents "Clear all"
 * button does — removeTask over binder — but keeping a caller-supplied set of
 * packages (the launcher, this app, and whatever is currently on each screen).
 *
 * Invoked by [ForegroundAppService.clearBackgroundApps] as:
 *   ... app_process ... app.wayfinder.RecentsTool "pkgA,pkgB,pkgC"
 * Prints "OK removed=N kept=K" on success, "ERR ..." otherwise.
 */
object RecentsTool {

    private const val MAX = 500

    /** iiSU fork: where Android keeps the tasks' last pictures (user 0). */
    private const val SNAPSHOTS = "/data/system_ce/0/snapshots"

    @JvmStatic
    fun main(args: Array<String>) {
        // iiSU fork: the multitask's list (RecentsSource) and a card's screenshot
        when (args.getOrNull(0)) {
            "list" -> return try { list(java.io.File(args[1])) } catch (t: Throwable) { fail(chain(t)) }
            "shot" -> return try { shot(args[1].toInt(), java.io.File(args[2])) } catch (t: Throwable) { fail(chain(t)) }
        }
        val keep = (args.getOrNull(0) ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "activity_task") as? IBinder
                ?: return fail("activity_task unavailable")
            val atm = Class.forName("android.app.IActivityTaskManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)!!

            val getRecents = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" }
                ?: return fail("getRecentTasks not found; " + sig(atm, "Recent"))
            // (maxNum int, flags int, userId int) — first int = maxNum, rest 0
            val gArgs = getRecents.parameterTypes.mapIndexed { i, t ->
                when {
                    t == Int::class.javaPrimitiveType && i == 0 -> MAX
                    t == Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }.toTypedArray()
            val raw = getRecents.invoke(atm, *gArgs)
            // getRecentTasks returns a ParceledListSlice — unwrap via getList()
            val list = when {
                raw is List<*> -> raw
                raw != null -> raw.javaClass.getMethod("getList").invoke(raw) as? List<*> ?: emptyList<Any?>()
                else -> emptyList<Any?>()
            }

            val removeTask = atm.javaClass.methods
                .firstOrNull { it.name == "removeTask" && it.parameterTypes.size == 1 }
                ?: return fail("removeTask not found; " + sig(atm, "remove"))

            var removed = 0
            var kept = 0
            val removedPkgs = StringBuilder()
            val keptPkgs = StringBuilder()
            for (rt in list) {
                val info = rt as? ActivityManager.RecentTaskInfo ?: continue
                val taskId = info.taskId
                if (taskId < 0) continue
                val pkg = info.baseActivity?.packageName
                    ?: info.topActivity?.packageName
                    ?: info.baseIntent?.component?.packageName
                    ?: info.baseIntent?.`package`
                if (pkg != null && pkg in keep) { kept++; keptPkgs.append(pkg).append(' '); continue }
                try { removeTask.invoke(atm, taskId); removed++; removedPkgs.append(pkg ?: "?").append(' ') } catch (_: Throwable) {}
            }
            println("OK removed=$removed kept=$kept | removed:[${removedPkgs.trim()}] kept:[${keptPkgs.trim()}]")
            halt(0)
        } catch (t: Throwable) {
            fail(chain(t))
        }
    }

    private fun recentTasks(): List<ActivityManager.RecentTaskInfo> {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as? IBinder ?: error("activity_task unavailable")
        val atm = Class.forName("android.app.IActivityTaskManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val getRecents = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" } ?: error("getRecentTasks not found; " + sig(atm, "Recent"))
        val gArgs = getRecents.parameterTypes.mapIndexed { i, t ->
            when { t == Int::class.javaPrimitiveType && i == 0 -> MAX; t == Int::class.javaPrimitiveType -> 0; else -> null }
        }.toTypedArray()
        val raw = getRecents.invoke(atm, *gArgs)
        val list = when { raw is List<*> -> raw; raw != null -> raw.javaClass.getMethod("getList").invoke(raw) as? List<*>; else -> null }
        return list.orEmpty().filterIsInstance<ActivityManager.RecentTaskInfo>()
    }

    // TaskInfo's displayId / lastActiveTime / topActivityType are @hide fields; no hidden-API checks in app_process (root)
    @android.annotation.SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi")
    private fun hidden(t: Any, name: String): Long = runCatching {
        android.app.TaskInfo::class.java.getDeclaredField(name).apply { isAccessible = true }.get(t).let { (it as Number).toLong() }
    }.getOrDefault(-1L)

    /**
     * `list <dir>`: [dir]/tasks.txt, one task a line (`taskId \t package \t display \t lastActiveTime \t activityType`, read by
     * RecentsModel.parse), and [dir]/<taskId>.jpg, each task's last picture as Android keeps it (the reduced one first).
     * Every file readable by the app (written as root); pictures of tasks no longer listed are deleted.
     */
    private fun list(dir: java.io.File) {
        val tasks = recentTasks().filter { it.taskId >= 0 }
        val lines = tasks.mapNotNull { t ->
            val pkg = t.baseActivity?.packageName ?: t.topActivity?.packageName ?: t.baseIntent?.component?.packageName ?: t.baseIntent?.`package`
            pkg?.let { "${t.taskId}\t$it\t${hidden(t, "displayId")}\t${hidden(t, "lastActiveTime")}\t${hidden(t, "topActivityType")}" }
        }
        val tmp = java.io.File(dir, "tasks.tmp")
        tmp.writeText(lines.joinToString("\n", postfix = "\n"))
        tmp.setReadable(true, false)
        if (!tmp.renameTo(java.io.File(dir, "tasks.txt"))) error("rename tasks.txt")
        val snapDir = java.io.File(SNAPSHOTS)
        var shots = 0
        for (t in tasks) {
            val src = listOf("${t.taskId}_reduced.jpg", "${t.taskId}.jpg").map { java.io.File(snapDir, it) }.firstOrNull { it.canRead() } ?: continue
            val out = java.io.File(dir, "${t.taskId}.jpg")
            if (runCatching { src.copyTo(out, overwrite = true); out.setReadable(true, false) }.isSuccess) shots++
        }
        val ids = tasks.map { "${it.taskId}.jpg" }.toSet()
        dir.listFiles { f -> f.name.matches(Regex("\\d+\\.jpg")) && f.name !in ids }?.forEach { it.delete() }
        println("OK tasks=${lines.size} shots=$shots snapdir=${if (snapDir.isDirectory) "yes" else "no"}")
        halt(0)
    }

    /** `shot <taskId> <dir>`: the task's full picture (the reduced one if that is all there is) as [dir]/shot.jpg. */
    private fun shot(taskId: Int, dir: java.io.File) {
        val src = listOf("$taskId.jpg", "${taskId}_reduced.jpg").map { java.io.File(SNAPSHOTS, it) }.firstOrNull { it.canRead() }
            ?: return fail("no picture for task $taskId")
        val out = java.io.File(dir, "shot.jpg")
        src.copyTo(out, overwrite = true); out.setReadable(true, false)
        println("OK"); halt(0)
    }

    private fun sig(obj: Any, needle: String): String =
        obj.javaClass.methods.filter { it.name.contains(needle) }.joinToString("; ") { m ->
            "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})"
        }

    private fun chain(t: Throwable): String {
        val sb = StringBuilder(); var c: Throwable? = t
        while (c != null) { sb.append(c.javaClass.name).append(": ").append(c.message).append(" | "); c = c.cause }
        return sb.toString()
    }

    private fun fail(reason: String) { println("ERR $reason"); halt(1) }

    private fun halt(code: Int) {
        System.out.flush(); System.err.flush(); Runtime.getRuntime().halt(code)
    }
}
