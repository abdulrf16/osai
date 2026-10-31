package com.fronx.smartlauncher.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.applications.AppInfo
import com.fronx.smartlauncher.applications.SearchField
import com.fronx.smartlauncher.applications.filterApps
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconButton
import com.fronx.smartlauncher.design.FronxIconView

/** Choose which installed apps appear in the dashboard's quick grid. Saved immediately. */
@Composable
fun FavoritesScreen(apps: List<AppInfo>, favorites: Set<String>, onToggle: (AppInfo) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fronx.colors
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(apps, query) { filterApps(apps, query, false, emptySet()) }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FronxIconButton(FronxIcon.Back, onBack)
            Spacer(Modifier.width(8.dp))
            Text("Favourite apps", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Spacer(Modifier.width(24.dp))
            SearchField(query, { query = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.packageName }) { app ->
                val fav = app.packageName in favorites
                FronxCard(Modifier.fillMaxWidth().height(64.dp), onClick = { onToggle(app) }, color = if (fav) c.elevated else c.card) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        app.icon?.let { Image(it, null, Modifier.size(40.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        FronxIconView(if (fav) FronxIcon.Star else FronxIcon.StarOutline, if (fav) c.accentSecondary else c.textMuted, size = 26.dp)
                    }
                }
            }
        }
    }
}
