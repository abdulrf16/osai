package com.fronx.smartlauncher.applications

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconButton
import com.fronx.smartlauncher.design.FronxIconView
import com.fronx.smartlauncher.design.FronxSegmented

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "Search apps") {
    val c = Fronx.colors
    Row(
        modifier.height(56.dp).clip(Fronx.buttonShape).background(c.card).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FronxIconView(FronxIcon.Search, c.textMuted, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(hint, color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) FronxIconButton(FronxIcon.Close, { onChange("") }, size = 40.dp, iconSize = 18.dp)
    }
}

/** Pure filter used by the drawer and the favourites picker (unit-tested). */
fun filterApps(apps: List<AppInfo>, query: String, favoritesOnly: Boolean, favorites: Set<String>): List<AppInfo> =
    apps.filter { (!favoritesOnly || it.packageName in favorites) && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(
    apps: List<AppInfo>?,
    favorites: Set<String>,
    columns: Int,
    onLaunch: (AppInfo) -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Fronx.colors
    var query by rememberSaveable { mutableStateOf("") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Applications", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Spacer(Modifier.width(24.dp))
            SearchField(query, { query = it }, Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            FronxSegmented(listOf(false, true), favOnly, { if (it) "Favourites" else "All" }, { favOnly = it })
        }
        val shown = remember(apps, query, favOnly, favorites) { filterApps(apps ?: emptyList(), query, favOnly, favorites) }
        when {
            apps == null -> Centered("Loading apps...")
            shown.isEmpty() -> Centered(
                when {
                    favOnly && query.isBlank() -> "No favourites yet. Long-press an app and choose Add to favourites."
                    query.isNotBlank() -> "No apps match \"${query.trim()}\""
                    else -> "No launchable apps found"
                }
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown, key = { it.packageName }) { app ->
                    Box(
                        Modifier.aspectRatio(1.15f).clip(Fronx.cardShape).background(c.card)
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { menuFor = app })
                    ) {
                        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            app.icon?.let { Image(it, null, Modifier.size(56.dp)) } ?: Spacer(Modifier.size(56.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(app.label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                        if (app.packageName in favorites)
                            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { FronxIconView(FronxIcon.Star, c.accentSecondary, size = 16.dp) }
                    }
                }
            }
        }
    }

    menuFor?.let { app ->
        val isFav = app.packageName in favorites
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = c.elevated,
            title = { Text(app.label, color = c.textPrimary) },
            text = {
                Column {
                    DialogAction(if (isFav) "Remove from favourites" else "Add to favourites") { onToggleFavorite(app); menuFor = null }
                    DialogAction("App info") { onAppInfo(app); menuFor = null }
                }
            },
            confirmButton = { TextButton({ menuFor = null }) { Text("Close", color = c.accentSecondary) } }
        )
    }
}

@Composable
private fun DialogAction(text: String, onClick: () -> Unit) {
    Text(text, color = Fronx.colors.textPrimary, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp))
}

@Composable
fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Fronx.colors.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
