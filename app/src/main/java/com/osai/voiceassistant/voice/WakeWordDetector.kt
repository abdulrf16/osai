package com.osai.voiceassistant.voice

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import com.osai.voiceassistant.utils.Constants
import com.osai.voiceassistant.utils.Logger
import java.io.File

/**
 * On-device, always-listening wake word detection for "Hey Assistant" using
 * the Picovoice Porcupine SDK. Porcupine runs entirely on-device (no audio
 * ever leaves the phone for wake word spotting) and is designed to be
 * lightweight enough to run continuously in a foreground service.
 *
 * Requires two developer-supplied artifacts that cannot be committed to a
 * public repo (per-account, licensed to a bundle id):
 *  1. A Picovoice AccessKey (https://console.picovoice.ai) placed in
 *     `assets/porcupine/access_key.txt`.
 *  2. A custom wake word model file for "Hey Assistant" trained on the
 *     Picovoice Console and placed at [Constants.PORCUPINE_MODEL_ASSET_PATH].
 *
 * If either artifact is missing, [start] fails gracefully and reports the
 * error via [WakeWordListener.onError] instead of crashing the service -
 * Riding Mode still works for on-demand commands, just without hands-free
 * wake word activation until the assets are provided.
 */
class WakeWordDetector(
    private val context: Context,
    private val listener: WakeWordListener
) {

    private var porcupineManager: PorcupineManager? = null
    @Volatile private var isListening = false

    fun start() {
        if (isListening) {
            Logger.d(TAG, "Wake word detector already listening")
            return
        }
        val accessKey = readAccessKey()
        if (accessKey.isNullOrBlank()) {
            Logger.e(TAG, "Missing Porcupine access key asset; wake word detection disabled")
            listener.onError("Wake word detection is not configured")
            return
        }
        if (!assetExists(Constants.PORCUPINE_MODEL_ASSET_PATH)) {
            Logger.e(TAG, "Missing Porcupine keyword model asset; wake word detection disabled")
            listener.onError("Wake word model is not installed")
            return
        }

        try {
            val callback = PorcupineManagerCallback { keywordIndex ->
                Logger.i(TAG, "Wake word detected (index=$keywordIndex)")
                listener.onWakeWordDetected()
            }
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(Constants.PORCUPINE_MODEL_ASSET_PATH)
                .setSensitivity(DEFAULT_SENSITIVITY)
                .build(context, callback)
            porcupineManager?.start()
            isListening = true
            Logger.i(TAG, "Wake word detector started")
        } catch (e: PorcupineException) {
            Logger.e(TAG, "Failed to start Porcupine wake word engine", e)
            listener.onError("Could not start wake word detection")
            isListening = false
        } catch (e: Exception) {
            Logger.e(TAG, "Unexpected error starting wake word engine", e)
            listener.onError("Could not start wake word detection")
            isListening = false
        }
    }

    /** Temporarily stop listening (e.g. while speech recognition owns the mic). */
    fun pause() {
        try {
            porcupineManager?.stop()
            isListening = false
        } catch (e: PorcupineException) {
            Logger.e(TAG, "Error pausing wake word engine", e)
        }
    }

    /** Resume listening after speech recognition / TTS has finished with the mic. */
    fun resume() {
        if (isListening) return
        try {
            porcupineManager?.start()
            isListening = true
        } catch (e: PorcupineException) {
            Logger.e(TAG, "Error resuming wake word engine", e)
            listener.onError("Could not resume wake word detection")
        }
    }

    fun stop() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Logger.e(TAG, "Error stopping wake word engine", e)
        } finally {
            porcupineManager = null
            isListening = false
            Logger.i(TAG, "Wake word detector stopped")
        }
    }

    fun isRunning(): Boolean = isListening

    private fun readAccessKey(): String? {
        return try {
            context.assets.open(ACCESS_KEY_ASSET_PATH).bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null
        }
    }

    private fun assetExists(path: String): Boolean {
        return try {
            val parent = File(path).parent ?: ""
            val fileName = File(path).name
            context.assets.list(parent)?.contains(fileName) == true
        } catch (e: Exception) {
            false
        }
    }

    interface WakeWordListener {
        fun onWakeWordDetected()
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "WakeWordDetector"
        private const val ACCESS_KEY_ASSET_PATH = "porcupine/access_key.txt"
        private const val DEFAULT_SENSITIVITY = 0.6f
    }
}
