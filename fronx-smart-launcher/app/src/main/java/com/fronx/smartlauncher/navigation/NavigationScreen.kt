package com.fronx.smartlauncher.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.applications.AppInfo
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxButton
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconButton
import com.fronx.smartlauncher.design.FronxIconView
import com.fronx.smartlauncher.preferences.Destination

/**
 * Navigation hub. No SDK, no location access: places are handed to whichever installed navigation app
 * the user picked, using documented VIEW intents (google.navigation: / geo:).
 */
@Composable
fun NavigationScreen(
    navApps: List<AppInfo>,
    preferredPackage: String?,
    destinations: List<Destination>,
    onOpenApp: (AppInfo) -> Unit,
    onOpenMaps: () -> Unit,
    mapsInstalled: Boolean,
    onSelectApp: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onAddDestination: (Destination) -> Unit,
    onRemoveDestination: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Fronx.colors
    var adding by remember { mutableStateOf(false) }
    Row(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Left: apps
        Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Navigation", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            FronxCard(Modifier.fillMaxWidth().height(96.dp), onClick = onOpenMaps, color = c.card) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                        FronxIconView(FronxIcon.Navigation, c.onAccent, size = 28.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (mapsInstalled) "Open Google Maps" else "Open maps app", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(if (mapsInstalled) "Installed" else "Opens the first map app found", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
            Text("NAVIGATION APP", style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
            if (navApps.isEmpty()) {
                Text("No navigation app found. Install a maps app to use destinations.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                navApps.forEach { app ->
                    val selected = app.packageName == preferredPackage
                    FronxCard(Modifier.fillMaxWidth().height(64.dp), onClick = { onSelectApp(app.packageName) },
                        color = if (selected) c.elevated else c.card) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            app.icon?.let { Image(it, null, Modifier.size(40.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                            if (selected) FronxIconView(FronxIcon.Check, c.accentSecondary, size = 22.dp)
                            else FronxButton("Open", { onOpenApp(app) }, modifier = Modifier.height(44.dp))
                        }
                    }
                }
            }
        }
        // Right: destinations
        Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Favourite destinations", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                FronxButton("Add", { adding = true }, icon = FronxIcon.Plus, primary = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Fuel station", "EV charging", "Parking", "Restaurant").forEach { q ->
                    FronxButton(q, { onNavigate(q) }, modifier = Modifier.weight(1f))
                }
            }
            if (destinations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No saved destinations yet.\nTap Add to save Home, Work or any address.",
                        style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                destinations.forEach { d ->
                    FronxCard(Modifier.fillMaxWidth().height(72.dp), onClick = { onNavigate(d.query) }) {
                        Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FronxIconView(FronxIcon.Pin, c.accentSecondary, size = 26.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
                                Text(d.query, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                            }
                            FronxIconButton(FronxIcon.Trash, { onRemoveDestination(d) }, size = 52.dp, iconSize = 22.dp)
                        }
                    }
                }
            }
        }
    }
    if (adding) AddDestinationDialog({ adding = false }) { onAddDestination(it); adding = false }
}

@Composable
private fun AddDestinationDialog(onDismiss: () -> Unit, onSave: (Destination) -> Unit) {
    val c = Fronx.colors
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.elevated,
        title = { Text("Add destination", color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Home)") }, singleLine = true)
                OutlinedTextField(query, { query = it }, label = { Text("Address or place") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && query.isNotBlank()) onSave(Destination(name.trim(), query.trim())) }) {
                Text("Save", color = c.accentSecondary)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.textSecondary) } }
    )
}
