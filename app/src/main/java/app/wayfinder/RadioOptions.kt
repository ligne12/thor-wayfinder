package app.wayfinder

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wayfinder.ui.FocusableGlass
import app.wayfinder.ui.GlassSwitchRow
import app.wayfinder.ui.LocalGlass
import app.wayfinder.ui.Radii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * iiSU fork: the Wi-Fi and Bluetooth tiles' options windows. Android lets no ordinary app join a saved network without its
 * password, or connect a paired device: a network or a device opens Android's own Wi-Fi panel or Bluetooth page, on the
 * top screen ([openSettings] closes the panel first).
 */

/** A row of the list: [text], and [status] on its right ("Connected"). */
@Composable
private fun RadioRow(text: String, status: String?, onClick: () -> Unit) {
    val g = LocalGlass.current
    FocusableGlass(onClick = onClick, modifier = Modifier.fillMaxWidth(), radius = Radii.Row) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, color = g.textPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (status != null) Text(status, color = g.accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, color = LocalGlass.current.textSecondary, style = MaterialTheme.typography.bodyMedium)

/** A root command's whole output: pservice hands back only the first line ([PServiceBridge.runEntryPoint]), so it goes
 *  through a file in the cache. "" when it can't run. */
private fun rootOutput(ctx: Context, cmd: String): String {
    val f = java.io.File(ctx.cacheDir, "radio_${System.nanoTime()}.txt")
    return try {
        PServiceBridge.exec("$cmd > ${f.absolutePath} 2>/dev/null; chmod 644 ${f.absolutePath}")
        runCatching { f.readText() }.getOrDefault("")
    } finally { f.delete() }
}

/** Wi-Fi: on / off (the tile's own action), the network it is on, the saved ones in range, Android's Wi-Fi settings. */
@Composable
fun WifiOptions(t: PanelTile, openSettings: (String) -> Unit) {
    val ctx = LocalContext.current
    GlassSwitchRow("Wi-Fi", t.on, { t.action() })
    if (!t.on) Note("Wi-Fi is off")
    else {
        // the app may not read the network's name (no location permission): root's `cmd wifi`, off the main thread. The
        // last scan first, then once more after a fresh one
        val read by produceState<Pair<String?, List<String>>?>(null) {
            suspend fun once() = withContext(Dispatchers.IO) {
                TileOptionRules.currentSsid(rootOutput(ctx, "cmd wifi status")) to
                    TileOptionRules.savedInRange(rootOutput(ctx, "cmd wifi list-networks"), rootOutput(ctx, "cmd wifi list-scan-results"))
            }
            value = once()
            withContext(Dispatchers.IO) { PServiceBridge.exec("cmd wifi start-scan") }
            delay(4000)
            value = once()
        }
        val r = read
        if (r == null) Note("Reading…")
        else {
            val (current, inRange) = r
            if (current != null) RadioRow(current, "Connected") { openSettings(Settings.ACTION_WIFI_SETTINGS) }
            val others = inRange - setOfNotNull(current)
            if (others.isEmpty()) Note(if (current == null) "No saved network in range" else "No other saved network in range")
            // Android's floating Wi-Fi panel: one tap there joins it
            for (ssid in others) RadioRow(ssid, null) { openSettings("android.settings.panel.action.WIFI") }
        }
    }
    OptionLink("Wi-Fi settings…") { openSettings(Settings.ACTION_WIFI_SETTINGS) }
}

/** Bluetooth: on / off, the paired devices (the connected ones first), Android's Bluetooth settings. */
@Composable
fun BluetoothOptions(t: PanelTile, openSettings: (String) -> Unit) {
    val ctx = LocalContext.current
    GlassSwitchRow("Bluetooth", t.on, { t.action() })
    if (!t.on) Note("Bluetooth is off")
    else {
        // null while it's read; then the list, itself null when it couldn't be read
        val devices by produceState<Result<List<Pair<String, Boolean>>?>?>(null) {
            value = Result.success(withContext(Dispatchers.IO) { paired(ctx) })
        }
        val d = devices?.getOrNull()
        when {
            devices == null -> Note("Reading…")
            d == null -> Note("Can't read the paired devices")
            d.isEmpty() -> Note("No paired device")
            else -> for ((name, connected) in d)
                RadioRow(name, if (connected) "Connected" else null) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
        }
    }
    OptionLink("Bluetooth settings…") { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
}

/** The paired devices (name, connected), connected first. Android 12+ wants "Nearby devices" for it: granted through root
 *  the first time. Null when it can't be read. */
@android.annotation.SuppressLint("MissingPermission")
private fun paired(ctx: Context): List<Pair<String, Boolean>>? {
    val perm = "android.permission.BLUETOOTH_CONNECT"
    fun granted() = android.os.Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    if (!granted()) PServiceBridge.exec("pm grant ${ctx.packageName} $perm")
    if (!granted()) return null
    val adapter = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return null
    return runCatching {
        adapter.bondedDevices.map { dev ->
            val name = runCatching { dev.alias ?: dev.name }.getOrNull() ?: dev.address
            // hidden (SystemApi), read by reflection; unknown: not connected
            val connected = runCatching { dev.javaClass.getMethod("isConnected").invoke(dev) as Boolean }.getOrDefault(false)
            name to connected
        }.sortedWith(compareByDescending<Pair<String, Boolean>> { it.second }.thenBy { it.first.lowercase() })
    }.getOrNull()
}
