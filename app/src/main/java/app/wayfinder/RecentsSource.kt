package app.wayfinder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * iiSU fork: the multitask's apps and their pictures, read as root by [RecentsTool]'s list mode into the cache
 * (`cache/recents`). [tasks] is the last list read (Compose state: an open [RecentsScreen] follows it); null: never read.
 */
object RecentsSource {
    var tasks by mutableStateOf<List<RecentTask>?>(null)
        private set
    /** Bumped by every successful read: a card decodes its picture again (an equal list wouldn't change [tasks]). */
    var reads by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dir(ctx: Context) = File(ctx.cacheDir, "recents").apply { mkdirs() }

    /** Reads the list again, off the main thread, one read at a time (a second call while one runs only gets `done(false)`). */
    fun refreshAsync(ctx: Context, done: (Boolean) -> Unit = {}) {
        val app = ctx.applicationContext
        if (!busy.compareAndSet(false, true)) { done(false); return }
        Thread {
            var read: List<RecentTask>? = null
            try {
                val d = dir(app)
                val out = PServiceBridge.runEntryPoint(app, "RecentsTool", "list", d.absolutePath)
                if (out?.startsWith("OK") == true) { read = RecentsModel.parse(File(d, "tasks.txt").readLines()); android.util.Log.d("ThorRecents", "list: $out") }
                else android.util.Log.w("ThorRecents", "list: $out")
            } catch (t: Throwable) {
                android.util.Log.w("ThorRecents", "list: ${t.javaClass.simpleName}")
            } finally {
                busy.set(false)
                main.post { if (read != null) { tasks = read; reads++ }; done(read != null) }
            }
        }.apply { isDaemon = true; name = "recents-list" }.start()
    }

    /** A card just closed: out of the list at once (the next open would show it until the list is read again). Main thread. */
    fun forget(taskId: Int) { tasks = tasks?.filter { it.taskId != taskId } }

    /** Opens [task] as Recents does, by its id ([RecentsTool]'s open mode), on its screen. Root: never the main thread. */
    fun open(ctx: Context, task: RecentTask): Boolean =
        PServiceBridge.runEntryPoint(ctx, "RecentsTool", "open", task.taskId.toString(), task.display.toString())?.startsWith("OK") == true

    /** The task's picture as last listed; null if there is none to read. */
    fun shotFile(ctx: Context, taskId: Int): File? = File(dir(ctx), "$taskId.jpg").takeIf { it.canRead() }

    /** The task's full picture into Pictures/Screenshots (Recents' own Screenshot). Root and storage: never the main thread. */
    fun saveScreenshot(ctx: Context, task: RecentTask): Boolean {
        val d = dir(ctx)
        val out = PServiceBridge.runEntryPoint(ctx, "RecentsTool", "shot", task.taskId.toString(), d.absolutePath)
        val shot = File(d, "shot.jpg")
        if (out?.startsWith("OK") != true || !shot.canRead()) { android.util.Log.w("ThorRecents", "shot: $out"); return false }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Screenshot_${stamp}_${task.pkg}.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching { resolver.openOutputStream(uri)!!.use { o -> shot.inputStream().use { it.copyTo(o) } } }
            .onFailure { resolver.delete(uri, null, null) }.isSuccess
    }
}
