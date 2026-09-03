package com.osai.voiceassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osai.voiceassistant.R
import com.osai.voiceassistant.service.ServiceStatus
import com.osai.voiceassistant.ui.components.StatusIndicator

/**
 * The single hands-free "riding" screen: a large on/off control, a
 * glanceable status indicator, and two debug affordances. Intentionally has
 * no scrolling, no dense text, and no small tap targets, since it's meant to
 * be used with gloves on and minimal visual attention.
 */
@Composable
fun RidingModeScreen(
    ridingModeEnabled: Boolean,
    status: ServiceStatus,
    bluetoothConnected: Boolean,
    onToggleRidingMode: (Boolean) -> Unit,
    onTestMic: () -> Unit,
    onTestSpeaker: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.settings_title))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(horizontal = 24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusIndicator(status = status)

                Spacer(modifier = Modifier.height(40.dp))

                RidingModeToggleButton(
                    enabled = ridingModeEnabled,
                    onToggle = onToggleRidingMode
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (ridingModeEnabled) stringResource(R.string.riding_mode_on) else stringResource(R.string.riding_mode_off),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (bluetoothConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(
                        text = if (bluetoothConnected) "Bluetooth headset connected" else "Using phone microphone",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onTestMic) {
                        Text(stringResource(R.string.debug_test_mic))
                    }
                    OutlinedButton(onClick = onTestSpeaker) {
                        Text(stringResource(R.string.debug_test_speaker))
                    }
                }
            }
        }
    }
}

@Composable
private fun RidingModeToggleButton(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.size(160.dp),
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        onClick = { onToggle(!enabled) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (enabled) "ON" else "OFF",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}
