package com.osai.voiceassistant.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.osai.voiceassistant.bluetooth.AudioRoutingPolicy
import com.osai.voiceassistant.bluetooth.BluetoothDeviceManager
import com.osai.voiceassistant.bluetooth.MicSource
import com.osai.voiceassistant.bluetooth.OutputSink
import com.osai.voiceassistant.utils.Logger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Routes microphone input to the connected Bluetooth headset (primary) or
 * the phone mic (fallback) using [AudioManager]'s SCO APIs, per
 * [AudioRoutingPolicy]. TextToSpeech output routing is handled implicitly by
 * the system once SCO is active; this class only needs to manage the SCO
 * connection lifecycle around each speech recognition session.
 */
class BluetoothAudioRouter(
    private val context: Context,
    private val bluetoothDeviceManager: BluetoothDeviceManager
) {

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var scoReceiver: BroadcastReceiver? = null

    fun currentMicSource(): MicSource =
        AudioRoutingPolicy.decideMicSource(bluetoothDeviceManager.isHeadsetConnected())

    fun currentOutputSink(): OutputSink =
        AudioRoutingPolicy.decideOutputSink(bluetoothDeviceManager.isHeadsetConnected())

    /**
     * Ensures the mic input path matches [AudioRoutingPolicy] before a
     * speech recognition session starts. Suspends briefly while SCO
     * connects when a Bluetooth headset is the target source; falls back to
     * the phone mic immediately if no headset is connected or SCO fails to
     * connect within [scoConnectTimeoutMs].
     */
    suspend fun prepareForListening(scoConnectTimeoutMs: Long = 3000L): MicSource {
        val target = currentMicSource()
        if (target == MicSource.PHONE_MIC) {
            stopBluetoothSco()
            return MicSource.PHONE_MIC
        }

        val connected = startBluetoothScoAndAwait(scoConnectTimeoutMs)
        return if (connected) {
            Logger.i(TAG, "Routed mic input to Bluetooth headset")
            MicSource.BLUETOOTH_HEADSET
        } else {
            Logger.w(TAG, "Bluetooth SCO failed to connect in time, falling back to phone mic")
            stopBluetoothSco()
            MicSource.PHONE_MIC
        }
    }

    fun releaseAfterListening() {
        stopBluetoothSco()
    }

    private suspend fun startBluetoothScoAndAwait(timeoutMs: Long): Boolean {
        if (audioManager.isBluetoothScoOn) return true

        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED && !resumed) {
                        resumed = true
                        if (continuation.isActive) continuation.resume(true)
                    } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR && !resumed) {
                        resumed = true
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
            scoReceiver = receiver
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to start Bluetooth SCO", e)
                if (!resumed) {
                    resumed = true
                    continuation.resume(false)
                }
            }

            val handler = android.os.Handler(context.mainLooper)
            val timeoutRunnable = Runnable {
                if (!resumed) {
                    resumed = true
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                unregisterScoReceiver()
            }
        }.also {
            unregisterScoReceiver()
        }
    }

    private fun stopBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping Bluetooth SCO", e)
        }
        unregisterScoReceiver()
    }

    private fun unregisterScoReceiver() {
        scoReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        scoReceiver = null
    }

    companion object {
        private const val TAG = "BluetoothAudioRouter"
    }
}
