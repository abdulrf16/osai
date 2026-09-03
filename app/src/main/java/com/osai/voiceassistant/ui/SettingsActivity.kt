package com.osai.voiceassistant.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osai.voiceassistant.VoiceAssistantTheme
import com.osai.voiceassistant.permissions.PermissionManager
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.ui.screens.MessagingAppOption
import com.osai.voiceassistant.ui.screens.SettingsScreen
import com.osai.voiceassistant.utils.Logger

/**
 * Settings for wake phrase (display-only in MVP 1), feedback verbosity,
 * which messaging apps get read aloud, and a permission status dashboard
 * for re-granting anything the user revoked later.
 */
class SettingsActivity : ComponentActivity() {

    /** Known messaging app packages we can offer to read notifications from. */
    private val candidateMessagingApps = linkedMapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "Messages (SMS)",
        "com.samsung.android.messaging" to "Samsung Messages (SMS)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = SharedPreferencesManager.getInstance(applicationContext)
        val permissionManager = PermissionManager(applicationContext)

        setContent {
            VoiceAssistantTheme {
                val settings by settingsManager.settings.collectAsStateWithLifecycle()
                val installedApps = remember { installedMessagingApps() }
                val permissionStatus = remember(settings) { permissionManager.checker.checkAll() }

                SettingsScreen(
                    wakePhrase = settings.wakePhrase,
                    feedbackVerbosity = settings.feedbackVerbosity,
                    onVerbosityChange = { settingsManager.updateFeedbackVerbosity(it) },
                    messagingApps = installedApps.map { (pkg, label) ->
                        MessagingAppOption(
                            packageName = pkg,
                            label = label,
                            isEnabled = pkg in settings.enabledMessagingPackages
                        )
                    },
                    onToggleMessagingApp = { pkg, enabled ->
                        val updated = settings.enabledMessagingPackages.toMutableSet()
                        if (enabled) updated.add(pkg) else updated.remove(pkg)
                        settingsManager.updateEnabledMessagingPackages(updated)
                        Logger.d(TAG, "Messaging app toggled: enabled=$enabled")
                    },
                    permissionItems = permissionManager.wizardSteps(),
                    permissionGranted = permissionStatus.granted,
                    onGrantPermission = { id ->
                        val intent = when (id) {
                            com.osai.voiceassistant.permissions.PermissionId.NOTIFICATION_LISTENER ->
                                permissionManager.notificationListenerSettingsIntent()
                            else -> permissionManager.appSettingsIntent()
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    private fun installedMessagingApps(): List<Pair<String, String>> {
        val pm = packageManager
        return candidateMessagingApps.mapNotNull { (pkg, label) ->
            try {
                pm.getPackageInfo(pkg, 0)
                pkg to label
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.ifEmpty {
            // If none of the known apps are installed (or QUERY_ALL_PACKAGES
            // isn't granted yet), still show the defaults so the user can
            // pre-configure preferences before installing/granting.
            candidateMessagingApps.entries.take(3).map { it.key to it.value }
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}
