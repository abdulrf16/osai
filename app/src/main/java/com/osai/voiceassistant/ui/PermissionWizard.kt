package com.osai.voiceassistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.osai.voiceassistant.permissions.PermissionId
import com.osai.voiceassistant.permissions.PermissionManager
import com.osai.voiceassistant.ui.screens.PermissionScreen

/**
 * Stateful host around [PermissionScreen]: owns the runtime permission
 * launcher and re-checks status whenever the user returns from a system
 * settings screen (e.g. after granting notification listener access).
 */
@Composable
fun PermissionWizard(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    var status by remember { mutableStateOf(permissionManager.checker.checkAll()) }

    fun refreshStatus() {
        status = permissionManager.checker.checkAll()
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStatus()
    }

    val specialAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStatus()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionScreen(
        items = permissionManager.wizardSteps(),
        granted = status.granted,
        allCriticalGranted = status.allCriticalGranted,
        onGrantPermission = { id ->
            when (id) {
                PermissionId.NOTIFICATION_LISTENER ->
                    specialAccessLauncher.launch(permissionManager.notificationListenerSettingsIntent())
                else -> {
                    val permissions = com.osai.voiceassistant.permissions.PermissionRegistry
                        .manifestPermissionsFor(id)
                        .toTypedArray()
                    if (permissions.isNotEmpty()) runtimeLauncher.launch(permissions)
                }
            }
        },
        onFinish = {
            com.osai.voiceassistant.settings.SharedPreferencesManager
                .getInstance(context)
                .setPermissionWizardCompleted(true)
            onCompleted()
        }
    )
}
