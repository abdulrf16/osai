package com.fronx.smartlauncher.connectivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconView

class Shortcut(val icon: FronxIcon, val title: String, val subtitle: String, val enabled: Boolean, val onClick: () -> Unit)

/** Big-target grid of shortcut cards (used by Phone and Quick Settings). */
@Composable
fun ShortcutGrid(items: List<Shortcut>, columns: Int, modifier: Modifier = Modifier, rowHeight: androidx.compose.ui.unit.Dp = 120.dp) {
    val c = Fronx.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { s ->
                    FronxCard(Modifier.weight(1f).height(rowHeight), onClick = if (s.enabled) s.onClick else null) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(c.elevated), contentAlignment = Alignment.Center) {
                                FronxIconView(s.icon, if (s.enabled) c.accentSecondary else c.textMuted, size = 28.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(s.title, style = MaterialTheme.typography.titleMedium, color = if (s.enabled) c.textPrimary else c.textMuted, maxLines = 1)
                                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2)
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun PhoneScreen(conn: ConnectivityState, dialerAvailable: Boolean, contactsAvailable: Boolean,
                onDialer: () -> Unit, onContacts: () -> Unit, onBluetooth: () -> Unit, onWifi: () -> Unit,
                modifier: Modifier = Modifier) {
    val c = Fronx.colors
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Phone & connectivity", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        ShortcutGrid(
            listOf(
                Shortcut(FronxIcon.Phone, "Phone", if (dialerAvailable) "Open the dialer" else "No phone app found", dialerAvailable, onDialer),
                Shortcut(FronxIcon.Person, "Contacts", if (contactsAvailable) "Open contacts" else "No contacts app found", contactsAvailable, onContacts),
                Shortcut(FronxIcon.Bluetooth, "Bluetooth", "Pair & manage devices - ${label(conn.bluetooth)}", true, onBluetooth),
                Shortcut(FronxIcon.Wifi, "Wi-Fi", "Networks - ${label(conn.wifi)}", true, onWifi)
            ),
            columns = 2, rowHeight = 130.dp
        )
        Text("Pairing and calls are handled by the head unit's own Bluetooth and phone apps. Fronx only opens them.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}

private fun label(s: RadioState) = when (s) {
    RadioState.CONNECTED -> "connected"; RadioState.ON -> "on"; RadioState.OFF -> "off"; RadioState.UNAVAILABLE -> "unavailable"
}
