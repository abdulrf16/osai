package com.osai.voiceassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osai.voiceassistant.permissions.PermissionId
import com.osai.voiceassistant.permissions.PermissionUiItem
import com.osai.voiceassistant.settings.FeedbackVerbosity
import com.osai.voiceassistant.ui.components.PermissionStatusCard

/** One installed app the user can toggle message reading for. */
data class MessagingAppOption(
    val packageName: String,
    val label: String,
    val isEnabled: Boolean
)

@Composable
fun SettingsScreen(
    wakePhrase: String,
    feedbackVerbosity: FeedbackVerbosity,
    onVerbosityChange: (FeedbackVerbosity) -> Unit,
    messagingApps: List<MessagingAppOption>,
    onToggleMessagingApp: (String, Boolean) -> Unit,
    permissionItems: List<PermissionUiItem>,
    permissionGranted: Map<PermissionId, Boolean>,
    onGrantPermission: (PermissionId) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValuesAll16, modifier = Modifier.fillMaxWidth()) {
            item { SectionTitle("Wake phrase") }
            item {
                Text(
                    text = "\"$wakePhrase\" (fixed in this version)",
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            item { SectionTitle("Feedback verbosity") }
            items(FeedbackVerbosity.entries.toList()) { verbosity ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = feedbackVerbosity == verbosity,
                        onClick = { onVerbosityChange(verbosity) }
                    )
                    Text(text = verbosity.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item { SectionTitle("Read messages from") }
            items(messagingApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = app.label, modifier = Modifier.padding(end = 8.dp))
                    Checkbox(
                        checked = app.isEnabled,
                        onCheckedChange = { checked -> onToggleMessagingApp(app.packageName, checked) }
                    )
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }

            item { SectionTitle("Permissions") }
            items(permissionItems, key = { it.id }) { item ->
                PermissionStatusCard(
                    titleRes = item.titleRes,
                    descriptionRes = item.descriptionRes,
                    isGranted = permissionGranted[item.id] == true,
                    onGrantClick = { onGrantPermission(item.id) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )
}

private val PaddingValuesAll16 = androidx.compose.foundation.layout.PaddingValues(16.dp)
