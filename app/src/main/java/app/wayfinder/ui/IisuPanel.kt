package app.wayfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iiSU fork: the bricks of iiSU's panels — its settings' look — shared by the volume bar, the notification center and
 * the control center.
 */

/** iiSU's menu card: light grey in light (a slider's white track and thumb read on it), the modal surface in dark. */
fun menuCard(g: GlassColors): Color = if (g.dark) g.surfaceModal else Color(IisuLook.MENU_LIGHT)

/** iiSU fork: how a toggle tile lays its content out — from its height, then whether it carries a value. */
enum class TileLayout { INLINE, TWO_LINES, NAME_VALUE, LARGE }

/** Under 48 dp the icon and the name sit side by side (no room for a value); under 72 dp a value takes the second line. */
internal fun tileLayout(heightDp: Float, hasValue: Boolean): TileLayout = when {
    heightDp < 48f -> TileLayout.INLINE
    heightDp < 72f -> if (hasValue) TileLayout.NAME_VALUE else TileLayout.TWO_LINES
    else -> TileLayout.LARGE
}

/**
 * A toggle tile, as the sound panel's ringer modes: an icon over its name in a 16 dp tile; on, it is filled with the
 * selection gradient (no tick mark). Focusable, with iiSU's focus band. [iconContent] draws the icon instead (an app's
 * own icon), in the tile's ink; [disabled] only fades the content — the action still runs and says why it can't.
 * Below 48 dp the icon and the name sit side by side. One explicit focus target, as [FocusableGlass]: it holds the focus in
 * touch mode too, reports it ([FocusTruth]) and is the window's last focused control ([LocalFocusMemory]) — a closed popup
 * gives the focus back to the tile that opened it.
 */
@Composable
fun IisuToggleTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, height: Dp = 72.dp,
                   description: String = label, value: String? = null, disabled: Boolean = false,
                   iconContent: (@Composable (tint: Color) -> Unit)? = null, focusRequester: FocusRequester? = null,
                   onClick: () -> Unit) {
    val g = LocalGlass.current
    val tile = RoundedCornerShape(16.dp)
    val gate = remember { ReleaseGate() }
    val click by rememberUpdatedState(onClick)   // the tap handler is installed once
    val own = remember { FocusRequester() }
    val req = focusRequester ?: own
    val reqNow by rememberUpdatedState(req)
    val mem = LocalFocusMemory.current
    val ink = if (on) g.onAccent else g.textPrimary
    val glyph: @Composable (Dp) -> Unit = { size ->
        if (iconContent != null) iconContent(ink) else Icon(icon, null, tint = ink, modifier = Modifier.size(size))
    }
    val fade = Modifier.alpha(if (disabled) 0.5f else 1f).padding(horizontal = 4.dp)
    Box(modifier.height(height)
        .focusRequester(req)
        .focusIndicator(tile, onFill = on)
        .onFocusChanged {
            FocusTruth.report(req, it.hasFocus)
            if (it.isFocused) mem?.last = req else gate.armed = false
        }
        .onKeyEvent { gate.onKey(it) { click() } }
        .then(if (on) Modifier.clip(tile).background(Brush.linearGradient(g.accentStops), tile)
              else Modifier.glassSurface(g, tile, well = g.surfaceModal))
        // not `clickable`: its focus is refused in touch mode (the quick panel opened with nothing focused)
        .focusable()
        .pointerInput(Unit) { detectTapGestures { mem?.last = reqNow; click() } }
        .semantics { contentDescription = description; onClick { click(); true } },
        contentAlignment = Alignment.Center) {
        when (tileLayout(height.value, value != null)) {
            TileLayout.INLINE -> Row(fade, verticalAlignment = Alignment.CenterVertically) {
                glyph(18.dp)
                Spacer(Modifier.width(6.dp))
                Text(label, color = ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TileLayout.NAME_VALUE -> Column(fade, horizontalAlignment = Alignment.CenterHorizontally) {
                glyph(22.dp)
                Text(label, color = ink, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value!!, color = if (on) g.onAccent else g.textSecondary, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // a low tile (the notification center's) takes a longer name on two smaller lines ("Do not disturb")
            TileLayout.TWO_LINES -> Column(fade, horizontalAlignment = Alignment.CenterHorizontally) {
                glyph(24.dp)
                Text(label, color = ink, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp)
            }
            TileLayout.LARGE -> Column(fade, horizontalAlignment = Alignment.CenterHorizontally) {
                glyph(24.dp)
                Text(label, color = ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** iiSU fork: a section's title, as "Launch Behavior" in iiSU's settings — icon, name, then a hairline to the edge. */
@Composable
fun IisuSectionTitle(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Row(modifier.fillMaxWidth().heightIn(min = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = g.textSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = g.textPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1, lineHeight = 16.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(g.textTertiary.copy(alpha = 0.35f)))
    }
}

/** iiSU fork: the control center's round button — a circle of [size] with a 20 dp icon. Also a page's Back (part 6) and the
 *  Hub's Ko-fi. */
@Composable
fun IisuRoundButton(icon: ImageVector, description: String, onClick: () -> Unit, focusRequester: FocusRequester? = null,
                    size: androidx.compose.ui.unit.Dp = 36.dp, modifier: Modifier = Modifier) {
    FocusableGlass(onClick = onClick, modifier = modifier.size(size), radius = size / 2, focusRequester = focusRequester,
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, description, tint = LocalGlass.current.textSecondary, modifier = Modifier.size(20.dp))
    }
}
