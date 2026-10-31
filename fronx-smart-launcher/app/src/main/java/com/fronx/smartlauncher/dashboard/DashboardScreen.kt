package com.fronx.smartlauncher.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fronx.smartlauncher.applications.QuickTile
import com.fronx.smartlauncher.connectivity.ConnectivityState
import com.fronx.smartlauncher.connectivity.RadioState
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxButton
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconButton
import com.fronx.smartlauncher.design.FronxIconView
import com.fronx.smartlauncher.media.MediaActions
import com.fronx.smartlauncher.media.MediaCard
import com.fronx.smartlauncher.media.MediaUiState
import com.fronx.smartlauncher.preferences.ClockFormat
import com.fronx.smartlauncher.preferences.DashboardLayout
import com.fronx.smartlauncher.preferences.LauncherPreferences
import com.fronx.smartlauncher.weather.WeatherUiState
import com.fronx.smartlauncher.utils.Format
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wall-clock ticking once per minute, only while the activity is started. */
@Composable
fun rememberNow(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.longValue = System.currentTimeMillis()
                delay(60_000L - now.longValue % 60_000L + 50L)
            }
        }
    }
    return now
}

fun formatTime(ms: Long, fmt: ClockFormat): Pair<String, String> {
    val loc = Locale.getDefault()
    return if (fmt == ClockFormat.H24) SimpleDateFormat("HH:mm", loc).format(Date(ms)) to ""
    else SimpleDateFormat("h:mm", loc).format(Date(ms)) to SimpleDateFormat("a", loc).format(Date(ms)).uppercase(loc)
}

class DashboardActions(
    val openTile: (QuickTile) -> Unit,
    val openNavigation: () -> Unit,
    val openPhone: () -> Unit,
    val openSettings: () -> Unit,
    val openAllApps: () -> Unit,
    val media: MediaActions,
    val setDefaultLauncher: () -> Unit
)

@Composable
fun DashboardScreen(
    prefs: LauncherPreferences,
    tiles: List<QuickTile>,
    connectivity: ConnectivityState,
    media: MediaUiState,
    weather: WeatherUiState,
    isDefaultHome: Boolean,
    actions: DashboardActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        StatusBar(prefs, connectivity, actions.openSettings)
        if (!isDefaultHome) DefaultHomeBanner(actions.setDefaultLauncher)
        val info: @Composable (Modifier) -> Unit = { InfoZone(prefs, weather, actions, it) }
        val mediaZone: @Composable (Modifier) -> Unit = { MediaCard(media, actions.media, it) }
        val apps: @Composable (Modifier) -> Unit = { QuickAppsZone(tiles, actions, it) }
        // Spec: ~27% / 43% / 30%. Hidden zones free their share for the rest.
        val zones = buildList<Pair<Float, @Composable (Modifier) -> Unit>> {
            add(0.27f to info)
            if (prefs.showMedia) add(0.43f to mediaZone)
            if (prefs.showQuickApps) add(0.30f to apps)
        }.let { if (prefs.layout == DashboardLayout.MIRRORED) it.reversed() else it }
        Row(
            Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            zones.forEach { (w, content) -> content(Modifier.weight(w).fillMaxHeight()) }
        }
    }
}

@Composable
private fun StatusBar(prefs: LauncherPreferences, conn: ConnectivityState, onSettings: () -> Unit) {
    val c = Fronx.colors
    val now by rememberNow()
    val (time, ampm) = formatTime(now, prefs.clockFormat)
    val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now))
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$time $ampm".trim(), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.width(14.dp))
        Text(date, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.weight(1f))
        StatusChip(FronxIcon.Bluetooth, "Bluetooth", conn.bluetooth)
        Spacer(Modifier.width(18.dp))
        StatusChip(FronxIcon.Wifi, "Wi-Fi", conn.wifi)
        Spacer(Modifier.width(8.dp))
        FronxIconButton(FronxIcon.Settings, onSettings, size = 48.dp, iconSize = 24.dp)
    }
}

@Composable
private fun StatusChip(icon: FronxIcon, name: String, state: RadioState) {
    val c = Fronx.colors
    val (tint, label) = when (state) {
        RadioState.CONNECTED -> c.accentSecondary to "Connected"
        RadioState.ON -> c.textPrimary to "On"
        RadioState.OFF -> c.textMuted to "Off"
        RadioState.UNAVAILABLE -> c.textMuted to "N/A"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FronxIconView(icon, tint, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(name, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    val c = Fronx.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)
            .clip(Fronx.smallShape).background(c.elevated).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Fronx isn't your default Home app yet. The original launcher stays untouched until you choose.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        FronxButton("Choose", onClick, primary = true, modifier = Modifier.height(44.dp))
    }
}

@Composable
private fun InfoZone(prefs: LauncherPreferences, weather: WeatherUiState, a: DashboardActions, modifier: Modifier) {
    val c = Fronx.colors
    val now by rememberNow()
    val (time, ampm) = formatTime(now, prefs.clockFormat)
    val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(now))
    BoxWithConstraints(modifier) {
        val clockSp = (maxWidth.value / 3.1f).coerceIn(44f, 84f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, fontSize = clockSp.sp, fontWeight = FontWeight.Light, color = c.textPrimary, maxLines = 1,
                        letterSpacing = (-1).sp, lineHeight = (clockSp * 1.05f).sp)
                    if (ampm.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(ampm, style = MaterialTheme.typography.titleMedium, color = c.accentSecondary,
                            modifier = Modifier.padding(bottom = (clockSp * 0.14f).dp))
                    }
                }
                Text(day, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(date, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
            if (prefs.showWeather) WeatherCard(prefs, weather, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            ShortcutButton(FronxIcon.Navigation, "Navigation", a.openNavigation)
            ShortcutButton(FronxIcon.Phone, "Phone", a.openPhone)
        }
    }
}

@Composable
private fun ShortcutButton(icon: FronxIcon, label: String, onClick: () -> Unit) {
    val c = Fronx.colors
    FronxCard(Modifier.fillMaxWidth().height(68.dp), onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.elevated), contentAlignment = Alignment.Center) {
                FronxIconView(icon, c.accentSecondary, size = 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
    }
}

@Composable
private fun WeatherCard(prefs: LauncherPreferences, w: WeatherUiState, modifier: Modifier) {
    val c = Fronx.colors
    FronxCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("WEATHER", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            when (w) {
                is WeatherUiState.Ready -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Format.temperature(w.data.tempC, prefs.temperatureUnit), fontSize = 34.sp,
                            fontWeight = FontWeight.Light, color = c.textPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(w.data.description.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        if (w.stale) "${w.data.place} - offline, last update ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(w.data.fetchedAt))}"
                        else w.data.place,
                        style = MaterialTheme.typography.bodySmall, color = if (w.stale) c.warning else c.textMuted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WeatherUiState.Loading -> Text("Loading...", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                WeatherUiState.Unavailable -> Text("Weather unavailable (offline)", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                WeatherUiState.NoCity -> Text("Set a city in Customize to see weather", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                WeatherUiState.NotConfigured -> Text("Not configured - build with a weather API key to enable", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun QuickAppsZone(tiles: List<QuickTile>, a: DashboardActions, modifier: Modifier) {
    val c = Fronx.colors
    Column(modifier) {
        Text("QUICK APPS", style = MaterialTheme.typography.labelSmall, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = 12.dp
            val rows = 4
            val tileH: Dp = ((maxHeight - gap * (rows - 1)) / rows).coerceAtLeast(84.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { t -> QuickTileView(t, a, Modifier.weight(1f).height(tileH)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTileView(t: QuickTile, a: DashboardActions, modifier: Modifier) {
    val c = Fronx.colors
    FronxCard(modifier, onClick = { a.openTile(t) }) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            when (t) {
                is QuickTile.App -> {
                    t.app.icon?.let { Image(it, null, Modifier.size(48.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text(t.app.label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                QuickTile.Bluetooth -> {
                    FronxIconView(FronxIcon.Bluetooth, c.accentSecondary, size = 40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Bluetooth", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                }
                QuickTile.AllApps -> {
                    FronxIconView(FronxIcon.Apps, c.accentSecondary, size = 40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("All apps", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                }
            }
        }
    }
}
