package com.osai.voiceassistant.media

import android.content.Context
import android.media.AudioManager
import com.osai.voiceassistant.utils.Logger

/**
 * Wraps [AudioManager] for STREAM_MUSIC volume control. Works regardless of
 * which media app is currently playing since it operates at the system
 * stream level.
 */
class AudioVolumeManager(context: Context) {

    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumeUp(): Boolean = adjustStream(AudioManager.ADJUST_RAISE)

    fun volumeDown(): Boolean = adjustStream(AudioManager.ADJUST_LOWER)

    /** @param percent 0-100 */
    fun setVolumePercent(percent: Int): Boolean {
        return try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val min = getMinStreamVolume()
            val target = (min + (max - min) * percent.coerceIn(0, 100) / 100.0).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            Logger.d(TAG, "Set STREAM_MUSIC volume to $target/$max")
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Missing permission to set volume", e)
            false
        }
    }

    fun currentVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = getMinStreamVolume()
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (max <= min) return 0
        return (((current - min).toDouble() / (max - min)) * 100).toInt().coerceIn(0, 100)
    }

    private fun getMinStreamVolume(): Int {
        return try {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } catch (e: NoSuchMethodError) {
            0
        }
    }

    private fun adjustStream(direction: Int): Boolean {
        return try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            Logger.d(TAG, "Adjusted STREAM_MUSIC direction=$direction")
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Missing permission to adjust volume", e)
            false
        }
    }

    companion object {
        private const val TAG = "AudioVolumeManager"
    }
}
