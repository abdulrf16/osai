package com.fronx.smartlauncher.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.applications.AppInfo
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxCard

@Composable
fun MusicScreen(state: MediaUiState, actions: MediaActions, musicApps: List<AppInfo>, onLaunch: (AppInfo) -> Unit, modifier: Modifier = Modifier) {
    val c = Fronx.colors
    Row(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(0.55f).fillMaxHeight()) {
            Text("Music", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            MediaCard(state, actions, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(0.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Music apps", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.padding(top = 6.dp))
            if (musicApps.isEmpty()) {
                Text("No music apps detected on this device.", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                musicApps.forEach { app ->
                    FronxCard(Modifier.fillMaxWidth().height(72.dp), onClick = { onLaunch(app) }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            app.icon?.let { Image(it, null, Modifier.size(44.dp)) }
                            Spacer(Modifier.width(16.dp))
                            Text(app.label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
