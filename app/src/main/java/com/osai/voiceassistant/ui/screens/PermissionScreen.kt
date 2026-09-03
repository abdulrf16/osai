package com.osai.voiceassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osai.voiceassistant.R
import com.osai.voiceassistant.permissions.PermissionId
import com.osai.voiceassistant.permissions.PermissionUiItem
import com.osai.voiceassistant.ui.components.PermissionStatusCard

/**
 * First-run onboarding screen: explains and requests every permission the
 * app needs before Riding Mode can be used, one clear list instead of
 * scattering system prompts across first use.
 */
@Composable
fun PermissionScreen(
    items: List<PermissionUiItem>,
    granted: Map<PermissionId, Boolean>,
    allCriticalGranted: Boolean,
    onGrantPermission: (PermissionId) -> Unit,
    onFinish: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.permission_wizard_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.permission_wizard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    PermissionStatusCard(
                        titleRes = item.titleRes,
                        descriptionRes = item.descriptionRes,
                        isGranted = granted[item.id] == true,
                        onGrantClick = { onGrantPermission(item.id) }
                    )
                }
            }

            Button(
                onClick = onFinish,
                enabled = allCriticalGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = if (allCriticalGranted) stringResource(R.string.permission_finish)
                    else stringResource(R.string.permission_continue)
                )
            }
        }
    }
}
