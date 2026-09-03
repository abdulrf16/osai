package com.osai.voiceassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osai.voiceassistant.bluetooth.BluetoothDeviceManager
import com.osai.voiceassistant.permissions.PermissionManager
import com.osai.voiceassistant.service.ServiceStatus
import com.osai.voiceassistant.service.VoiceActivationService
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.ui.PermissionWizard
import com.osai.voiceassistant.ui.screens.RidingModeScreen
import com.osai.voiceassistant.utils.Constants
import com.osai.voiceassistant.utils.Logger

/**
 * Single launcher Activity. Hosts the first-run [PermissionWizard] until
 * every critical permission is granted, then hosts the always-on
 * [RidingModeScreen]. Kept as one Activity (rather than a separate
 * RidingModeActivity) to avoid an unnecessary extra Activity + back-stack
 * for what is effectively a two-state onboarding flow.
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SharedPreferencesManager
    private lateinit var bluetoothDeviceManager: BluetoothDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SharedPreferencesManager.getInstance(applicationContext)
        bluetoothDeviceManager = BluetoothDeviceManager(applicationContext)

        setContent {
            VoiceAssistantTheme {
                var showWizard by remember {
                    mutableStateOf(!currentPermissionsSatisfied())
                }

                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            bluetoothDeviceManager.start()
                            bluetoothDeviceManager.refreshState()
                            if (!showWizard && !currentPermissionsSatisfied()) {
                                showWizard = true
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    bluetoothDeviceManager.start()
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        bluetoothDeviceManager.stop()
                    }
                }

                if (showWizard) {
                    PermissionWizard(onCompleted = { showWizard = false })
                } else {
                    RidingModeHost(
                        settingsManager = settingsManager,
                        bluetoothDeviceManager = bluetoothDeviceManager,
                        onOpenSettings = {
                            startActivity(Intent(this, com.osai.voiceassistant.ui.SettingsActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    private fun currentPermissionsSatisfied(): Boolean {
        return PermissionManager(applicationContext).checker.checkAll().allCriticalGranted
    }
}

@Composable
private fun RidingModeHost(
    settingsManager: SharedPreferencesManager,
    bluetoothDeviceManager: BluetoothDeviceManager,
    onOpenSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val serviceStatus by VoiceActivationService.status.collectAsStateWithLifecycle()
    val bluetoothState by bluetoothDeviceManager.state.collectAsStateWithLifecycle()

    // Derive a display-only status when the service isn't running yet so the
    // indicator doesn't show a stale LISTENING state from a previous session.
    val displayStatus = if (settings.ridingModeEnabled) serviceStatus else ServiceStatus.STOPPED

    RidingModeScreen(
        ridingModeEnabled = settings.ridingModeEnabled,
        status = displayStatus,
        bluetoothConnected = bluetoothState.headsetConnected,
        onToggleRidingMode = { enable ->
            Logger.i("MainActivity", "Riding mode toggled: $enable")
            val intent = Intent(context, VoiceActivationService::class.java).apply {
                action = if (enable) Constants.ACTION_START_RIDING_MODE else Constants.ACTION_STOP_RIDING_MODE
            }
            if (enable) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        },
        onTestMic = {
            val intent = Intent(context, VoiceActivationService::class.java).apply {
                action = Constants.ACTION_TEST_MIC
            }
            ContextCompat.startForegroundService(context, intent)
        },
        onTestSpeaker = {
            val intent = Intent(context, VoiceActivationService::class.java).apply {
                action = Constants.ACTION_TEST_SPEAKER
            }
            ContextCompat.startForegroundService(context, intent)
        },
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun VoiceAssistantTheme(content: @Composable () -> Unit) {
    val colorScheme = androidx.compose.material3.darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF1565C0),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = androidx.compose.ui.graphics.Color(0xFF0D1B2A),
        surface = androidx.compose.ui.graphics.Color(0xFF1B263B),
        onBackground = androidx.compose.ui.graphics.Color(0xFFE0E1DD),
        onSurface = androidx.compose.ui.graphics.Color(0xFFE0E1DD)
    )
    androidx.compose.material3.MaterialTheme(colorScheme = colorScheme, content = content)
}
