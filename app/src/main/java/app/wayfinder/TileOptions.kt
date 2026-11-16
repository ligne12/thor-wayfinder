package app.wayfinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.wayfinder.ui.FocusableGlass
import app.wayfinder.ui.GlassSheet
import app.wayfinder.ui.IisuToggleTile
import app.wayfinder.ui.LocalGlass
import app.wayfinder.ui.ModalHeader
import app.wayfinder.ui.Radii
import app.wayfinder.ui.menuCard

/**
 * iiSU fork: a panel tile's options — what a long press on it opens, as iOS's control center does (Wi-Fi networks, a value
 * picked directly instead of pressing three times…). Built in [PanelShortcuts.tiles] beside the tile's short action, so
 * both read and write the same value.
 */
sealed class TileOptions {
    /** One of [choices]: a pick runs [onPick] (its index), then the window closes. [footer]: a link or a switch under them. */
    class Choices(val choices: List<Choice>, val onPick: (Int) -> Unit,
                  val footer: (@Composable () -> Unit)? = null) : TileOptions()
    /** Its own content (sliders, networks…), given its tile (its on / off switch is the tile's own action); the window stays open. */
    class Custom(val content: @Composable (PanelTile) -> Unit) : TileOptions()
}

class Choice(val label: String, val selected: Boolean)

/** The long press of panel tile [t]: its options (with a buzz), else its "why not" when [PanelTile.sayOnLong], else nothing. */
@Composable
fun tileLongPress(t: PanelTile, open: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        when {
            t.options != null -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); open() }
            t.sayOnLong -> t.action()
        }
    }
}

/** A link at the foot of an options window ("Fan curve…"), drawn as "App pairs"' last row. */
@Composable
fun OptionLink(text: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    FocusableGlass(onClick = onClick, modifier = Modifier.fillMaxWidth(), radius = Radii.Row) {
        Text(text, color = g.accent, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** [t]'s options window: "App pairs"' sheet, capped for the wide top screen. B, a tap outside or a pick shut it. */
@Composable
fun TileOptionsSheet(t: PanelTile, onClose: () -> Unit) {
    val options = t.options ?: return
    val first = remember { FocusRequester() }
    // the cap first: after fillMaxWidth its exact width can't be narrowed (it spread over the whole top screen).
    // The sound panel's card (iiSU's light grey): on the white one a slider's white track and thumb vanished
    GlassSheet(onDismiss = onClose, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f),
        first = if (options is TileOptions.Choices) first else null, contentPadding = 0.dp, well = menuCard(LocalGlass.current)) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Thomas: no Close button — B, a tap outside or a pick close it
            ModalHeader(t.label, onClose, closeVisible = false)
            when (options) {
                is TileOptions.Choices -> {
                    // the chosen one in the selection gradient, as the sound panel's ringer modes; the focus opens on it
                    val focusAt = options.choices.indexOfFirst { it.selected }.coerceAtLeast(0)
                    options.choices.withIndex().chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (i, c) ->
                                IisuToggleTile(null, c.label, c.selected, Modifier.weight(1f), height = 56.dp,
                                    focusRequester = if (i == focusAt) first else null) { options.onPick(i); onClose() }
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                    options.footer?.invoke()
                }
                is TileOptions.Custom -> options.content(t)
            }
        }
    }
}
