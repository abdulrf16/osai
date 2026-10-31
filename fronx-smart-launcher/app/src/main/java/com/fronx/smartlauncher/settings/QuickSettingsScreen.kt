package com.fronx.smartlauncher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.connectivity.Shortcut
import com.fronx.smartlauncher.connectivity.ShortcutGrid
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxButton
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon

class QuickSettingsActions(
    val display: () -> Unit, val sound: () -> Unit, val brightness: () -> Unit, val bluetooth: () -> Unit,
    val wifi: () -> Unit, val system: () -> Unit, val customize: () -> Unit,
    val chooseDefaultHome: () -> Unit, val notificationAccess: () -> Unit, val clearCrash: () -> Unit
)

/** Quick access to documented Android settings screens. Nothing here changes system state directly. */
@Composable
fun QuickSettingsScreen(
    isDefaultHome: Boolean,
    hasNotificationAccess: Boolean,
    lastCrash: String?,
    actions: QuickSettingsActions,
    modifier: Modifier = Modifier
) {
    val c = Fronx.colors
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        ShortcutGrid(
            listOf(
                Shortcut(FronxIcon.Display, "Display", "Opens Android display settings", true, actions.display),
                Shortcut(FronxIcon.Volume, "Sound", "Opens Android sound settings", true, actions.sound),
                Shortcut(FronxIcon.Brightness, "Brightness", "Opens Display settings (no direct control)", true, actions.brightness),
                Shortcut(FronxIcon.Bluetooth, "Bluetooth", "Opens Bluetooth settings", true, actions.bluetooth),
                Shortcut(FronxIcon.Wifi, "Wi-Fi", "Opens Wi-Fi settings", true, actions.wifi),
                Shortcut(FronxIcon.Settings, "Android settings", "Opens the system Settings app", true, actions.system),
                Shortcut(FronxIcon.Tune, "Customize launcher", "Theme, clock, layout, favourites", true, actions.customize)
            ),
            columns = 3, rowHeight = 104.dp
        )
        Text("LAUNCHER", style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
        InfoCard(
            title = if (isDefaultHome) "Fronx is your default Home app" else "Fronx is not the default Home app",
            body = "To switch back to the original launcher: open Choose default Home app (below) and select the original one, " +
                "or press Home and pick it when Android asks. Fronx never replaces the system launcher silently, " +
                "and can be uninstalled from Android's Settings > Apps at any time.",
            button = "Choose default Home app", onClick = actions.chooseDefaultHome
        )
        InfoCard(
            title = if (hasNotificationAccess) "Media access: allowed" else "Media access: not allowed",
            body = "Notification access lets Fronx see which app is playing music so it can show the track and offer " +
                "play/pause/next/previous. Fronx never reads your notifications. Android never lets an app grant this itself.",
            button = "Open notification access", onClick = actions.notificationAccess
        )
        if (lastCrash != null) InfoCard(
            title = "Last crash report", body = lastCrash.take(600), button = "Dismiss", onClick = actions.clearCrash
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String, button: String, onClick: () -> Unit) {
    val c = Fronx.colors
    FronxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.padding(8.dp))
            FronxButton(button, onClick)
        }
    }
}
