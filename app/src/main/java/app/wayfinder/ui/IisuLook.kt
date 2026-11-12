package app.wayfinder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * iiSU's appearance (Settings › Appearance › Style and Glass), the source of Wayfinder's look in this fork.
 * A null field means "not read": the default applies. Format: docs/superpowers/specs/iisu-settings-format.md.
 */
data class IisuLook(
    val dark: Boolean? = null,        // null: iiSU follows the system, or nothing was read
    val accent: Int? = null,          // ARGB
    val glassOpacity: Float? = null,  // 0..1
    val glassTint: Int? = null,       // ARGB, alpha ignored
    val glassOutline: Boolean? = null,
    val font: String? = null,
) {
    /** The four colours the theme builder takes (glassColorsFor's "your own colours" path), for the light or dark look. */
    fun colours(dark: Boolean): CustomColours {
        val opacity = (glassOpacity ?: if (dark) 0.60f else 0.72f).coerceIn(0f, 1f)
        val tint = glassTint ?: if (dark) 0x2B2B2B else 0xFFFFFF
        return CustomColours(
            bg = if (dark) 0xFF1C1C20.toInt() else 0xFFF2F2F2.toInt(),
            card = (tint and 0x00FFFFFF) or (Math.round(opacity * 255) shl 24),
            text = if (dark) 0xFFF2F2F2.toInt() else 0xFF2B2B2B.toInt(),
            accent = (accent ?: 0xFF6C7BFF.toInt()) or 0xFF000000.toInt(),
            outline = glassOutline ?: true,
        )
    }
}

/** The last look read from iiSU; [IisuLook] defaults until then. Compose state: an open window follows a new read. */
object IisuLookSource {
    var current by mutableStateOf(IisuLook())
}
