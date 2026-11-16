package app.wayfinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wayfinder.ui.GlassSlider
import app.wayfinder.ui.GlassSwitchRow
import app.wayfinder.ui.IisuToggleTile
import app.wayfinder.ui.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** iiSU fork: the options windows with their own content ([TileOptions.Custom]) — Night light, Extra dim, the speaker. */

/** A slider in iiSU's look under its [label]. [write] runs 150 ms after the last move: one root command per gesture, not
 *  one per step. */
@Composable
private fun OptionSlider(label: String, initial: Float, step: Float = 0.05f, write: (Float) -> Unit) {
    val g = LocalGlass.current
    var v by remember { mutableFloatStateOf(initial) }
    var moved by remember { mutableStateOf(false) }
    LaunchedEffect(v) { if (moved) { delay(150); write(v) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = g.textPrimary, style = MaterialTheme.typography.bodyMedium)
        GlassSlider(v, Modifier.semantics { contentDescription = label }, step = step) { v = it; moved = true }
    }
}

/** A framework integer (config_…), else [fallback]. */
private fun systemInt(name: String, fallback: Int): Int = android.content.res.Resources.getSystem().let { r ->
    r.getIdentifier(name, "integer", "android").takeIf { it != 0 }?.let { runCatching { r.getInteger(it) }.getOrNull() }
} ?: fallback

/** A setting an app may not read on Android 13 (hidden keys throw), read through root off the main thread: "Reading…"
 *  until it answers, then [content] with it ([default] if it was never set). */
@Composable
private fun WithRootSetting(ns: String, key: String, default: Int, content: @Composable (Int) -> Unit) {
    val value by produceState<Int?>(null, ns, key) {
        value = withContext(Dispatchers.IO) {
            PServiceBridge.exec("settings get $ns $key")?.trim()?.toIntOrNull() ?: default
        }
    }
    val v = value
    if (v == null) Text("Reading…", color = LocalGlass.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
    else content(v)
}

/** Night light: on / off (the tile's own action), then how warm. */
@Composable
fun NightLightOptions(t: PanelTile) {
    GlassSwitchRow("Night light", t.on, { t.action() })
    val min = remember { systemInt("config_nightDisplayColorTemperatureMin", TileOptionRules.NIGHT_MIN) }
    val max = remember { systemInt("config_nightDisplayColorTemperatureMax", TileOptionRules.NIGHT_MAX) }
    val default = remember { systemInt("config_nightDisplayColorTemperatureDefault", 2850) }
    WithRootSetting("secure", "night_display_color_temperature", default) { k ->
        OptionSlider("Intensity", TileOptionRules.fractionOf(k, min, max)) {
            QuickSettings.exec("settings put secure night_display_color_temperature ${TileOptionRules.kelvinAt(it, min, max)}")
        }
    }
}

/** Extra dim: on / off, then how dim (0-100). */
@Composable
fun ExtraDimOptions(t: PanelTile) {
    GlassSwitchRow("Extra dim", t.on, { t.action() })
    val default = remember { systemInt("config_reduceBrightColorsStrengthDefault", 0) }
    WithRootSetting("secure", "reduce_bright_colors_level", default) { level ->
        OptionSlider("Intensity", level.coerceIn(0, 100) / 100f) {
            QuickSettings.exec("settings put secure reduce_bright_colors_level ${Math.round(it * 100)}")
        }
    }
}

/** The speaker sound fix, its equalizer (dimmed while the fix is off, as the "Speaker EQ" tile), the volume boost, the
 *  stereo width. SpeakerTune's values are Compose state: the window follows them. */
@Composable
fun SpeakerOptions() {
    val g = LocalGlass.current
    GlassSwitchRow("Speaker sound fix", SpeakerTune.enabled, { SpeakerTune.setOn(it) })
    GlassSwitchRow("Equalizer", SpeakerTune.enabled && SpeakerTune.eqOn, { if (SpeakerTune.enabled) SpeakerTune.setEq(it) },
        modifier = Modifier.alpha(if (SpeakerTune.enabled) 1f else 0.5f),
        explanation = if (SpeakerTune.enabled) null else "Turn on the speaker sound fix first")
    Text("Volume boost", color = g.textPrimary, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, db) in listOf("Off" to 0, "+6 dB" to 6, "+12 dB" to 12))
            IisuToggleTile(null, label, SpeakerTune.boost == db, Modifier.weight(1f), height = 44.dp) { SpeakerTune.setVolumeBoost(db) }
    }
    // 1 (no widening) to 3
    OptionSlider("Stereo width", (SpeakerTune.width - 1f) / 2f, step = 0.1f) { SpeakerTune.setStereoWidth(1f + it * 2f) }
}
