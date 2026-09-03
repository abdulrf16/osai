package com.osai.voiceassistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.osai.voiceassistant.utils.Logger

/**
 * Requests transient audio focus for TTS feedback / message read-outs so
 * that background music ducks briefly instead of colliding with speech, and
 * releases focus as soon as the utterance completes.
 */
class AudioFocusManager(context: Context) {

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null

    fun requestFocusForSpeech(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                Logger.d(TAG, "Audio focus changed: $change")
            }
            .build()

        return try {
            val result = audioManager.requestAudioFocus(request)
            focusRequest = request
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to request audio focus", e)
            false
        }
    }

    fun abandonFocus() {
        val request = focusRequest ?: return
        try {
            audioManager.abandonAudioFocusRequest(request)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to abandon audio focus", e)
        } finally {
            focusRequest = null
        }
    }

    companion object {
        private const val TAG = "AudioFocusManager"
    }
}
