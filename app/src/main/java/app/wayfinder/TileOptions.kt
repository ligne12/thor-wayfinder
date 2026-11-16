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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.wayfinder.ui.GlassSheet
import app.wayfinder.ui.IisuToggleTile
import app.wayfinder.ui.ModalHeader

/**
 * iiSU fork: a panel tile's options — what a long press on it opens, as iOS's control center does (Wi-Fi networks, a value
 * picked directly instead of pressing three times…). Built in [PanelShortcuts.tiles] beside the tile's short action, so
 * both read and write the same value.
 */
sealed class TileOptions {
    /** One of [choices]: a pick runs [onPick] (its index), then the window closes. [footer]: a link or a switch under them. */
    class Choices(val choices: List<Choice>, val onPick: (Int) -> Unit,
                  val footer: (@Composable () -> Unit)? = null) : TileOptions()
    /** Its own content (sliders, networks…); the window stays open. */
    class Custom(val content: @Composable () -> Unit) : TileOptions()
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

/** [t]'s options window: "App pairs"' sheet, capped for the wide top screen. B, a tap outside or Close shut it. */
@Composable
fun TileOptionsSheet(t: PanelTile, onClose: () -> Unit) {
    val options = t.options ?: return
    val first = remember { FocusRequester() }
    GlassSheet(onDismiss = onClose, modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp),
        first = if (options is TileOptions.Choices) first else null, contentPadding = 0.dp) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ModalHeader(t.label, onClose)
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
                is TileOptions.Custom -> options.content()
            }
        }
    }
}
