package com.osai.voiceassistant.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osai.voiceassistant.audio.TextToSpeechManager
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.utils.Logger

/**
 * Manifest-registered receiver for Bluetooth ACL / headset connection
 * broadcasts. Gives a short spoken confirmation when the rider's headset
 * connects or disconnects while Riding Mode is active, and nudges the
 * running [com.osai.voiceassistant.service.VoiceActivationService] (if any)
 * to re-evaluate its audio routing via [AudioRoutingPolicy].
 */
class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ridingModeEnabled = SharedPreferencesManager.getInstance(context).current().ridingModeEnabled
        if (!ridingModeEnabled) return

        when (intent.action) {
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                when (state) {
                    BluetoothHeadset.STATE_CONNECTED -> announce(context, "Bluetooth headset connected")
                    BluetoothHeadset.STATE_DISCONNECTED -> announce(context, "Bluetooth headset disconnected, switching to phone microphone")
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Logger.d(TAG, "Bluetooth ACL connected")
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Logger.d(TAG, "Bluetooth ACL disconnected")
            }
        }
    }

    /**
     * Fire-and-forget spoken announcement using a short-lived TTS instance.
     * A BroadcastReceiver has no long-lived lifecycle to bind an existing
     * engine to, so a dedicated instance is created, used once, and released.
     */
    private fun announce(context: Context, text: String) {
        Logger.i(TAG, "Bluetooth state change announcement triggered")
        val tts = TextToSpeechManager(context.applicationContext)
        android.os.Handler(context.mainLooper).postDelayed({
            tts.speak(text)
            android.os.Handler(context.mainLooper).postDelayed({ tts.shutdown() }, 4000)
        }, 400)
    }

    companion object {
        private const val TAG = "BluetoothStateReceiver"
    }
}
