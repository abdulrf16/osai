package com.osai.voiceassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.osai.voiceassistant.utils.Constants
import com.osai.voiceassistant.utils.Logger
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Outcome of a single speech recognition attempt. */
sealed class SpeechResult {
    data class Success(val transcript: String) : SpeechResult()
    data class Error(val reason: String) : SpeechResult()
    object NoSpeechDetected : SpeechResult()
}

/**
 * Thin coroutine-friendly wrapper around Android's on-device
 * [SpeechRecognizer]. Audio input source (Bluetooth headset mic vs phone
 * mic) is decided upstream by [BluetoothAudioRouter] and applied by the
 * system automatically once SCO audio is routed - this class is only
 * responsible for the recognition session itself.
 */
class CommandSpeechRecognizer(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Listens once for a single command and returns the best transcript.
     * Never throws - all failure paths resolve to [SpeechResult.Error] or
     * [SpeechResult.NoSpeechDetected].
     */
    suspend fun listenOnce(timeoutMs: Long = Constants.SPEECH_RECOGNITION_TIMEOUT_MS): SpeechResult {
        if (!isAvailable()) {
            Logger.e(TAG, "SpeechRecognizer not available on this device")
            return SpeechResult.Error("Speech recognition not available")
        }

        return suspendCancellableCoroutine { continuation ->
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            var resumed = false
            fun finish(result: SpeechResult) {
                if (resumed) return
                resumed = true
                if (continuation.isActive) continuation.resume(result)
            }

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Logger.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Logger.d(TAG, "Speech input started")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Logger.d(TAG, "Speech input ended")
                }

                override fun onError(error: Int) {
                    val reason = describeError(error)
                    Logger.w(TAG, "Speech recognition error: $reason")
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        finish(SpeechResult.NoSpeechDetected)
                    } else {
                        finish(SpeechResult.Error(reason))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()
                    if (best.isNullOrBlank()) {
                        finish(SpeechResult.NoSpeechDetected)
                    } else {
                        Logger.d(TAG, "Recognition succeeded (${Logger.redacted(best)})")
                        finish(SpeechResult.Success(best))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                sr.startListening(intent)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to start speech recognition", e)
                finish(SpeechResult.Error("Could not start listening"))
            }

            continuation.invokeOnCancellation {
                runCatching { sr.stopListening() }
                runCatching { sr.destroy() }
            }
        }.also {
            releaseRecognizer()
        }
    }

    fun cancel() {
        runCatching { recognizer?.stopListening() }
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Unknown error ($error)"
    }

    companion object {
        private const val TAG = "CommandSpeechRecognizer"
    }
}
