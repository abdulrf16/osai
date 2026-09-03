package com.osai.voiceassistant.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.osai.voiceassistant.service.NotificationMessageReader

/**
 * Registry of runtime permissions grouped by rider-facing [PermissionId].
 * The manifest permission list per id is computed from the running SDK
 * version via [manifestPermissionsFor], which is a pure function (no
 * Context needed) so it is directly unit testable.
 */
object PermissionRegistry {

    fun manifestPermissionsFor(id: PermissionId, sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        return when (id) {
            PermissionId.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
            PermissionId.BLUETOOTH -> if (sdkInt >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else {
                listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
            }
            PermissionId.PHONE_STATE -> listOf(Manifest.permission.READ_PHONE_STATE)
            PermissionId.NOTIFICATIONS -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
            PermissionId.NOTIFICATION_LISTENER -> emptyList() // special access, not a runtime permission
        }
    }

    /** All ids that require a standard runtime permission dialog. */
    val RUNTIME_PERMISSION_IDS = listOf(
        PermissionId.MICROPHONE,
        PermissionId.BLUETOOTH,
        PermissionId.PHONE_STATE,
        PermissionId.NOTIFICATIONS
    )

    val ALL_IDS = PermissionId.entries
}

/**
 * Android-facing permission checks. Delegates the "which manifest strings
 * map to which id" decision to the pure [PermissionRegistry] so that logic
 * stays testable, while this class does the actual [PackageManager] /
 * [Settings] lookups that require a [Context].
 */
class PermissionChecker(private val context: Context) {

    fun checkAll(): PermissionStatus {
        val granted = PermissionRegistry.ALL_IDS.associateWith { id -> isGranted(id) }
        return PermissionStatus(granted)
    }

    fun isGranted(id: PermissionId): Boolean {
        return when (id) {
            PermissionId.NOTIFICATION_LISTENER -> isNotificationListenerEnabled()
            else -> {
                val permissions = PermissionRegistry.manifestPermissionsFor(id)
                if (permissions.isEmpty()) true
                else permissions.all { hasManifestPermission(it) }
            }
        }
    }

    fun missingRuntimePermissions(): List<String> {
        return PermissionRegistry.RUNTIME_PERMISSION_IDS
            .flatMap { PermissionRegistry.manifestPermissionsFor(it) }
            .filterNot { hasManifestPermission(it) }
    }

    private fun hasManifestPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationListenerEnabled(): Boolean {
        // Definitive check: is our service actually connected right now.
        if (NotificationMessageReader.isListenerConnected()) return true

        // Fallback check via the system setting (covers "granted but the
        // listener hasn't been (re)bound yet" during app startup).
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expectedComponent = "${context.packageName}/${NotificationMessageReader::class.java.name}"
        return enabledListeners.contains(expectedComponent)
    }
}
