package com.osai.voiceassistant.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.osai.voiceassistant.R

/** UI-facing description of one permission step in the wizard/dashboard. */
data class PermissionUiItem(
    val id: PermissionId,
    val titleRes: Int,
    val descriptionRes: Int,
    val isSpecialAccess: Boolean
)

/**
 * Bridges [PermissionRegistry]/[PermissionChecker] to the UI layer: supplies
 * the ordered wizard steps and the intents needed to grant special-access
 * permissions that can't be requested via the standard runtime dialog
 * (notification listener access).
 */
class PermissionManager(private val context: Context) {

    val checker = PermissionChecker(context)

    fun wizardSteps(): List<PermissionUiItem> = listOf(
        PermissionUiItem(PermissionId.MICROPHONE, R.string.perm_record_audio_title, R.string.perm_record_audio_desc, false),
        PermissionUiItem(PermissionId.BLUETOOTH, R.string.perm_bluetooth_title, R.string.perm_bluetooth_desc, false),
        PermissionUiItem(PermissionId.PHONE_STATE, R.string.perm_phone_state_title, R.string.perm_phone_state_desc, false),
        PermissionUiItem(PermissionId.NOTIFICATIONS, R.string.perm_notifications_title, R.string.perm_notifications_desc, false),
        PermissionUiItem(PermissionId.NOTIFICATION_LISTENER, R.string.perm_notification_listener_title, R.string.perm_notification_listener_desc, true)
    )

    /** Manifest permission strings to pass to a single ActivityResultContracts.RequestMultiplePermissions launcher. */
    fun runtimePermissionsToRequest(): Array<String> {
        return PermissionRegistry.RUNTIME_PERMISSION_IDS
            .flatMap { PermissionRegistry.manifestPermissionsFor(it) }
            .distinct()
            .toTypedArray()
    }

    fun notificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun appSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    fun batteryOptimizationSettingsIntent(): Intent {
        // Samsung One UI honours the standard AOSP intent for requesting
        // exemption from battery optimization; users are guided here rather
        // than the app calling REQUEST_IGNORE_BATTERY_OPTIMIZATIONS silently,
        // to keep the ask transparent.
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
