package com.fronx.smartlauncher.media

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxButton
import com.fronx.smartlauncher.design.FronxCard
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconButton
import com.fronx.smartlauncher.design.FronxIconView
import com.fronx.smartlauncher.utils.Format
import kotlinx.coroutines.delay

class MediaActions(
    val togglePlayPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val openApp: () -> Unit,
    val openAccessSettings: () -> Unit,
    val browseMusicApps: () -> Unit
)

@Composable
fun MediaCard(state: MediaUiState, actions: MediaActions, modifier: Modifier = Modifier) {
    FronxCard(modifier, color = Fronx.colors.card) {
        when (state) {
            MediaUiState.AccessRequired -> Placeholder(
                title = "Show what's playing",
                body = "Android only lets a launcher see other apps' media after you allow Notification access. " +
                    "Fronx uses it only to read media sessions - it never reads your notifications.",
                button = "Open notification access", onClick = actions.openAccessSettings
            )
            MediaUiState.NoSession -> Placeholder(
                title = "Nothing playing",
                body = "Start playback in any music app and it will appear here with controls.",
                button = "Choose a music app", onClick = actions.browseMusicApps
            )
            is MediaUiState.Active -> ActiveMedia(state, actions)
        }
    }
}

@Composable
private fun Placeholder(title: String, body: String, button: String, onClick: () -> Unit) {
    val c = Fronx.colors
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)).background(c.elevated), contentAlignment = Alignment.Center) {
            FronxIconView(FronxIcon.Music, c.accentSecondary, size = 40.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        FronxButton(button, onClick, primary = true)
    }
}

@Composable
private fun ActiveMedia(s: MediaUiState.Active, a: MediaActions) {
    val c = Fronx.colors
    BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp)) {
        val artSize: Dp = minOf(maxWidth * 0.52f, maxHeight * 0.40f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Media app + status
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.appName, color = c.accentSecondary, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(s.statusLabel, color = c.textMuted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(10.dp))
            // Artwork (or a clean placeholder when the app supplies none)
            Box(
                Modifier.size(artSize).clip(RoundedCornerShape(18.dp)).background(c.elevated)
                    .clickable { a.openApp() },
                contentAlignment = Alignment.Center
            ) {
                if (s.art != null) {
                    Image(s.art.asImageBitmap(), contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else FronxIconView(FronxIcon.Music, c.textMuted, size = artSize * 0.4f)
            }
            Spacer(Modifier.height(12.dp))
            Text(s.title ?: "Unknown track", style = MaterialTheme.typography.titleLarge, color = c.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(s.artist ?: s.album ?: " ", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Progress(s)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FronxIconButton(FronxIcon.Previous, a.previous, enabled = s.canPrevious, description = "Previous track", size = 64.dp, iconSize = 30.dp)
                FronxIconButton(if (s.isPlaying) FronxIcon.Pause else FronxIcon.Play, a.togglePlayPause,
                    enabled = s.canPlayPause, filled = true, description = "Play or pause", size = 72.dp, iconSize = 34.dp)
                FronxIconButton(FronxIcon.Next, a.next, enabled = s.canNext, description = "Next track", size = 64.dp, iconSize = 30.dp)
            }
        }
    }
}

/** Progress is only drawn when the app reports both position and duration. Ticks only while playing and visible. */
@Composable
private fun Progress(s: MediaUiState.Active) {
    val c = Fronx.colors
    if (!s.hasProgress) { Spacer(Modifier.height(18.dp)); return }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(s.isPlaying, s.positionMs, s.updatedAtElapsed, owner) {
        now = SystemClock.elapsedRealtime()
        if (s.isPlaying) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(1000) }
        }
    }
    val pos = Format.livePosition(s.positionMs, s.updatedAtElapsed, s.speed, s.isPlaying, now, s.durationMs)
    val frac = (pos.toFloat() / s.durationMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.elevated)) {
            Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(Format.duration(pos), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            Spacer(Modifier.weight(1f))
            Text(Format.duration(s.durationMs), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
}
