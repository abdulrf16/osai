package com.osai.voiceassistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.osai.voiceassistant.utils.Logger
import java.util.Collections
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wraps [TextToSpeech] to provide spoken feedback ("Next song", "Volume
 * down", incoming call announcements, message read-outs). Audio focus is
 * requested/abandoned around each utterance via [AudioFocusManager] so that
 * speech ducks or pauses playback appropriately and always tries to route to
 * a connected Bluetooth headset first, falling back to the phone speaker.
 *
 * A single [UtteranceProgressListener] is installed once and dispatches by
 * utteranceId, rather than being replaced per-call, so that fire-and-forget
 * [speak] calls and awaited [speakAndAwait] calls never clobber each other's
 * completion callbacks.
 */
class TextToSpeechManager(
    context: Context,
    private val audioFocusManager: AudioFocusManager = AudioFocusManager(context)
) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    private val pendingUtterances = Collections.synchronizedList(mutableListOf<String>())
    private val awaitingCompletion = Collections.synchronizedMap(
        mutableMapOf<String, CancellableContinuation<Unit>>()
    )

    init {
        tts = TextToSpeech(appContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.getDefault()
                Logger.i(TAG, "TextToSpeech engine initialized")
                flushPending()
            } else {
                Logger.e(TAG, "TextToSpeech engine failed to initialize, status=$status")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Logger.d(TAG, "Speaking utterance started")
            }

            override fun onDone(utteranceId: String?) {
                audioFocusManager.abandonFocus()
                completeAwaiter(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                audioFocusManager.abandonFocus()
                Logger.w(TAG, "Utterance failed to speak")
                completeAwaiter(utteranceId)
            }
        })
    }

    /**
     * Speaks [text] immediately, interrupting any in-progress utterance.
     * Never throws - failures are logged and swallowed so a TTS glitch never
     * crashes the foreground service.
     */
    fun speak(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        if (!isReady) {
            pendingUtterances.add(text)
            Logger.w(TAG, "TTS not ready yet, queued utterance")
            return
        }
        try {
            val granted = audioFocusManager.requestFocusForSpeech()
            if (!granted) {
                Logger.w(TAG, "Audio focus not granted, speaking anyway at reduced priority")
            }
            val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = UUID.randomUUID().toString()
            tts?.speak(text, queueMode, null, utteranceId)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to speak utterance", e)
            audioFocusManager.abandonFocus()
        }
    }

    /** Suspends until the utterance has finished playing, or [timeoutMs] elapses. */
    suspend fun speakAndAwait(text: String, timeoutMs: Long = 8000L) {
        if (text.isBlank() || !isReady) {
            speak(text)
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        val engine = tts ?: run {
            speak(text)
            return
        }

        suspendCancellableCoroutine<Unit> { continuation ->
            awaitingCompletion[utteranceId] = continuation
            audioFocusManager.requestFocusForSpeech()

            val handler = android.os.Handler(appContext.mainLooper)
            val timeoutRunnable = Runnable { completeAwaiter(utteranceId) }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            try {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to speak utterance", e)
                handler.removeCallbacks(timeoutRunnable)
                completeAwaiter(utteranceId)
            }

            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                awaitingCompletion.remove(utteranceId)
                audioFocusManager.abandonFocus()
            }
        }
    }

    private fun completeAwaiter(utteranceId: String?) {
        val id = utteranceId ?: return
        val continuation = awaitingCompletion.remove(id) ?: return
        if (continuation.isActive) continuation.resume(Unit)
    }

    fun stop() {
        tts?.stop()
        audioFocusManager.abandonFocus()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        awaitingCompletion.clear()
        audioFocusManager.abandonFocus()
        Logger.i(TAG, "TextToSpeech engine shut down")
    }

    private fun flushPending() {
        if (pendingUtterances.isEmpty()) return
        val toSpeak = ArrayList(pendingUtterances)
        pendingUtterances.clear()
        toSpeak.forEach { speak(it, interrupt = false) }
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
    }
}
