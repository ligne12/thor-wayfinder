package app.wayfinder

/** iiSU fork: a notification's button the center shows — one without a written reply; [index] in its `notification.actions`. */
data class NotifAction(val title: String, val index: Int)

/** iiSU fork: a notification as the center shows it ([NotificationsListener] reads it from Android). */
data class NotifItem(val key: String, val pkg: String, val postTime: Long, val title: String?, val text: String?, val clearable: Boolean,
                     val group: String?, val summary: Boolean, val actions: List<NotifAction>)

/** iiSU fork: the notification center's rules, pure so they are tested off the device. */
object NotificationModel {
    /** A finger that went down in the top strip: a pull opens the center — down at least [minPx], more down than sideways. */
    fun pullDown(dy: Float, dx: Float, minPx: Float): Boolean = dy >= minPx && dy > kotlin.math.abs(dx)

    /** The cards: a group's summary only when none of its notifications is there (as Android's shade), the newest first. */
    fun visible(items: List<NotifItem>): List<NotifItem> {
        val withChildren = items.filter { !it.summary && it.group != null }.mapNotNull { it.group }.toSet()
        return items.filterNot { it.summary && it.group in withChildren }.sortedByDescending { it.postTime }
    }

    /** "Clear notifications" has something to clear (ongoing ones stay). */
    fun anyClearable(items: List<NotifItem>): Boolean = items.any { it.clearable }
}
