package com.fronx.smartlauncher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxButton
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconButton
import com.fronx.smartlauncher.design.FronxSegmented
import com.fronx.smartlauncher.design.FronxSwitch
import com.fronx.smartlauncher.preferences.AccentOption
import com.fronx.smartlauncher.preferences.ClockFormat
import com.fronx.smartlauncher.preferences.DashboardLayout
import com.fronx.smartlauncher.preferences.LauncherPreferences
import com.fronx.smartlauncher.preferences.TemperatureUnit
import com.fronx.smartlauncher.preferences.WallpaperStyle

@Composable
fun CustomizationScreen(
    prefs: LauncherPreferences,
    favoriteCount: Int,
    onChange: ((LauncherPreferences) -> LauncherPreferences) -> Unit,
    onPickWallpaper: () -> Unit,
    onEditFavorites: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Fronx.colors
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FronxIconButton(FronxIcon.Back, onBack)
            Spacer(Modifier.width(8.dp))
            Text("Customize launcher", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        }
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("Clock & weather") {
                    Setting("Clock format") {
                        FronxSegmented(ClockFormat.entries, prefs.clockFormat, { if (it == ClockFormat.H12) "12-hour" else "24-hour" },
                            { v -> onChange { it.copy(clockFormat = v) } })
                    }
                    Setting("Temperature") {
                        FronxSegmented(TemperatureUnit.entries, prefs.temperatureUnit, { if (it == TemperatureUnit.CELSIUS) "°C" else "°F" },
                            { v -> onChange { it.copy(temperatureUnit = v) } })
                    }
                    var city by remember(prefs.weatherCity) { mutableStateOf(prefs.weatherCity) }
                    OutlinedTextField(city, { city = it; onChange { p -> p.copy(weatherCity = it) } }, singleLine = true,
                        label = { Text("Weather city (needs API key at build time)") }, modifier = Modifier.fillMaxWidth())
                }
                Section("Appearance") {
                    Setting("Dark theme") { FronxSwitch(prefs.darkTheme) { v -> onChange { it.copy(darkTheme = v) } } }
                    Setting("Accent colour") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentOption.entries.forEach { a ->
                                val sel = a == prefs.accent
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(Color(a.primary))
                                        .border(if (sel) 3.dp else 0.dp, c.textPrimary, CircleShape)
                                        .clickable { onChange { it.copy(accent = a) } },
                                    contentAlignment = Alignment.Center
                                ) { if (sel) com.fronx.smartlauncher.design.FronxIconView(FronxIcon.Check, Color.White, size = 22.dp) }
                            }
                        }
                    }
                    Text("Wallpaper", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FronxSegmented(WallpaperStyle.entries.filter { it != WallpaperStyle.CUSTOM }.let { it + WallpaperStyle.CUSTOM },
                            prefs.wallpaper, { it.label }, { v ->
                                if (v == WallpaperStyle.CUSTOM && prefs.customWallpaperUri == null) onPickWallpaper()
                                else onChange { it.copy(wallpaper = v) }
                            })
                    }
                    FronxButton("Choose image...", onPickWallpaper, icon = FronxIcon.Image)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("Dashboard") {
                    Setting("Layout") {
                        FronxSegmented(DashboardLayout.entries, prefs.layout, { it.label }, { v -> onChange { it.copy(layout = v) } })
                    }
                    Setting("Show weather") { FronxSwitch(prefs.showWeather) { v -> onChange { it.copy(showWeather = v) } } }
                    Setting("Show media card") { FronxSwitch(prefs.showMedia) { v -> onChange { it.copy(showMedia = v) } } }
                    Setting("Show quick apps") { FronxSwitch(prefs.showQuickApps) { v -> onChange { it.copy(showQuickApps = v) } } }
                }
                Section("Applications") {
                    Text("App drawer grid size", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    FronxSegmented(LauncherPreferences.GRID_SIZES, prefs.gridColumns, { "$it columns" }, { v -> onChange { it.copy(gridColumns = v) } })
                    Setting("Favourite apps ($favoriteCount)") { FronxButton("Edit", onEditFavorites, icon = FronxIcon.Star) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Fronx.colors
    FronxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            content()
        }
    }
}

@Composable
private fun Setting(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Fronx.colors.textPrimary, modifier = Modifier.weight(1f))
        control()
    }
}
