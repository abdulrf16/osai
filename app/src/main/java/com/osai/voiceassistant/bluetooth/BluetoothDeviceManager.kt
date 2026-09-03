package com.osai.voiceassistant.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.ContextCompat
import com.osai.voiceassistant.permissions.PermissionId
import com.osai.voiceassistant.permissions.PermissionRegistry
import com.osai.voiceassistant.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rider-facing snapshot of the connected Bluetooth audio accessory, if any. */
data class BluetoothAudioState(
    val headsetConnected: Boolean = false,
    val deviceName: String? = null
)

/**
 * Wraps the [BluetoothHeadset] profile proxy to detect when a Bluetooth
 * headset/earbuds (e.g. generic BT mic headset or Samsung Buds Pro) are
 * connected, so [voice.BluetoothAudioRouter] and [AudioDeviceListener] can
 * decide where to route mic input and TTS output.
 */
class BluetoothDeviceManager(private val context: Context) {

    private val _state = MutableStateFlow(BluetoothAudioState())
    val state: StateFlow<BluetoothAudioState> = _state.asStateFlow()

    private var headsetProxy: BluetoothHeadset? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HEADSET) return
            headsetProxy = proxy as BluetoothHeadset
            refreshState()
            Logger.i(TAG, "BluetoothHeadset profile proxy connected")
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HEADSET) return
            headsetProxy = null
            _state.value = BluetoothAudioState()
            Logger.i(TAG, "BluetoothHeadset profile proxy disconnected")
        }
    }

    /** Starts listening for headset profile connectivity. No-op if BT permission is missing. */
    fun start() {
        if (!hasBluetoothPermission()) {
            Logger.w(TAG, "Bluetooth permission not granted, skipping profile registration")
            return
        }
        val adapter = bluetoothAdapter ?: run {
            Logger.w(TAG, "No Bluetooth adapter available on this device")
            return
        }
        try {
            adapter.getProfileProxy(context.applicationContext, profileListener, BluetoothProfile.HEADSET)
        } catch (e: SecurityException) {
            Logger.e(TAG, "SecurityException registering Bluetooth profile proxy", e)
        }
    }

    fun stop() {
        val adapter = bluetoothAdapter
        val proxy = headsetProxy
        if (adapter != null && proxy != null) {
            try {
                adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
            } catch (e: Exception) {
                Logger.e(TAG, "Error closing Bluetooth profile proxy", e)
            }
        }
        headsetProxy = null
    }

    /** Re-reads connected devices from the profile proxy; call after ACL connect/disconnect broadcasts. */
    fun refreshState() {
        if (!hasBluetoothPermission()) return
        try {
            val proxy = headsetProxy
            val connected = proxy?.connectedDevices?.firstOrNull()
            _state.value = BluetoothAudioState(
                headsetConnected = connected != null,
                deviceName = connected?.name
            )
        } catch (e: SecurityException) {
            Logger.e(TAG, "SecurityException reading connected Bluetooth devices", e)
        }
    }

    fun isHeadsetConnected(): Boolean = _state.value.headsetConnected

    private fun hasBluetoothPermission(): Boolean {
        return PermissionRegistry.manifestPermissionsFor(PermissionId.BLUETOOTH).all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val TAG = "BluetoothDeviceManager"
    }
}
