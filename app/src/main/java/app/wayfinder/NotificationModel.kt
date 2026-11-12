package app.wayfinder

/** iiSU fork: the notification center's rules, pure so they are tested off the device. */
object NotificationModel {
    /** A finger that went down in the top strip: a pull opens the center — down at least [minPx], more down than sideways. */
    fun pullDown(dy: Float, dx: Float, minPx: Float): Boolean = dy >= minPx && dy > kotlin.math.abs(dx)
}
