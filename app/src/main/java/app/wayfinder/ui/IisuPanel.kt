package app.wayfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * A toggle tile, as the sound panel's ringer modes: an icon over its name in a 16 dp tile; on, it is filled with the
 * selection gradient (no tick mark). Focusable, with iiSU's focus band.
 */
@Composable
fun IisuToggleTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, height: Dp = 72.dp,
                   description: String = label, onClick: () -> Unit) {
    val g = LocalGlass.current
    val tile = RoundedCornerShape(16.dp)
    val gate = androidx.compose.runtime.remember { ReleaseGate() }
    Column(modifier.height(height)
        .focusIndicator(tile)
        .onKeyEvent { gate.onKey(it, onClick) }
        .then(if (on) Modifier.clip(tile).background(Brush.linearGradient(g.accentStops), tile)
              else Modifier.glassSurface(g, tile, well = g.surfaceModal, veil = false))
        .clickable(onClick = onClick)
        .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val ink = if (on) g.onAccent else g.textPrimary
        Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
        // a low tile (the notification center's) takes a longer name on two smaller lines ("Do not disturb")
        if (height < 72.dp) Text(label, color = ink, style = MaterialTheme.typography.labelMedium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
        else Text(label, color = ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
