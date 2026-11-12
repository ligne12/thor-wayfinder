package app.wayfinder

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * iiSU fork: Wayfinder reads the notifications for its notification center ([NotificationCenter]). The list is read again,
 * whole, whenever one comes or goes; the center acts through the companion (main thread; nothing while not connected).
 * Access is granted once through the root service ([ensureAccess]).
 */
class NotificationsListener : NotificationListenerService() {
    companion object {
        private const val TAG = "ThorNotifs"
        /** The active notifications, as Android lists them (Compose state). */
        var items by mutableStateOf<List<NotifItem>>(emptyList())
            private set
        @Volatile private var live: NotificationsListener? = null
        private val main = android.os.Handler(android.os.Looper.getMainLooper())

        /**
         * Asks Android to bind the listener again when it isn't connected: its first connection after the access was
         * granted came over a minute late on the Thor, and nothing reaches it until then.
         */
        fun reconnectIfNeeded(ctx: Context) {
            if (live == null) runCatching { requestRebind(ComponentName(ctx.applicationContext, NotificationsListener::class.java)) }
        }

        /** Its app, as a tap on the notification opens it; gone after if the app asked so (auto-cancel). */
        fun open(key: String) {
            val s = live ?: return
            val sbn = s.find(key) ?: return
            send(sbn.notification.contentIntent)
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) runCatching { s.cancelNotification(key) }
        }

        fun dismiss(key: String) { runCatching { live?.cancelNotification(key) } }

        /** Every notification that can be cleared (ongoing ones stay, as in Android's shade). */
        fun clearAll() { runCatching { live?.cancelAllNotifications() } }

        /** The notification's button [index] (one without a written reply). */
        fun act(key: String, index: Int) { send(live?.find(key)?.notification?.actions?.getOrNull(index)?.actionIntent) }

        // an app may have cancelled it meanwhile: nothing then
        private fun send(pi: PendingIntent?) {
            try { pi?.send() } catch (e: PendingIntent.CanceledException) { android.util.Log.w(TAG, "cancelled intent") }
        }

        /** Grants Wayfinder notification access through the root service, once (off the main thread). */
        fun ensureAccess(ctx: Context) {
            val app = ctx.applicationContext
            Thread {
                val nm = app.getSystemService(android.app.NotificationManager::class.java)
                val me = ComponentName(app, NotificationsListener::class.java)
                if (!nm.isNotificationListenerAccessGranted(me) && PServiceBridge.isAvailable())
                    PServiceBridge.exec("cmd notification allow_listener ${me.flattenToString()}")
            }.apply { isDaemon = true; name = "notif-access" }.start()
        }

        private fun toItem(sbn: StatusBarNotification): NotifItem {
            val n = sbn.notification
            val e = n.extras
            fun text(k: String) = e.getCharSequence(k)?.toString()?.takeIf { it.isNotBlank() }
            val actions = n.actions.orEmpty().mapIndexedNotNull { i, a ->
                if (a.remoteInputs.isNullOrEmpty() && !a.title.isNullOrBlank()) NotifAction(a.title.toString(), i) else null
            }
            return NotifItem(sbn.key, sbn.packageName, sbn.postTime, text(Notification.EXTRA_TITLE),
                text(Notification.EXTRA_BIG_TEXT) ?: text(Notification.EXTRA_TEXT), sbn.isClearable,
                if (sbn.isGroup) sbn.groupKey else null, n.flags and Notification.FLAG_GROUP_SUMMARY != 0, actions)
        }
    }

    private fun find(key: String): StatusBarNotification? = runCatching { activeNotifications?.firstOrNull { it.key == key } }.getOrNull()

    private fun reread() {
        val list = runCatching { activeNotifications?.map { toItem(it) } }.getOrNull().orEmpty()
        android.util.Log.d(TAG, "items: ${list.map { it.key }}")
        main.post { items = list }
    }

    override fun onListenerConnected() { live = this; reread() }
    override fun onListenerDisconnected() { if (live === this) live = null; main.post { items = emptyList() } }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = reread()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = reread()
}
