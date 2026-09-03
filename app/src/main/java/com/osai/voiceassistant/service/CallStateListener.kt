package com.osai.voiceassistant.service

import android.content.Context
import com.osai.voiceassistant.audio.TextToSpeechManager
import com.osai.voiceassistant.call.CallStateMonitor
import com.osai.voiceassistant.utils.Logger

/**
 * Bridges [com.osai.voiceassistant.call.CallStateReceiver] (a short-lived
 * BroadcastReceiver) to spoken announcements. When [VoiceActivationService]
 * is running it reuses that service's already-initialized, Bluetooth-routed
 * [TextToSpeechManager] (avoids a second engine fighting for audio focus);
 * otherwise it lazily creates its own so call announcements still work even
 * if the rider hasn't started Riding Mode's foreground listening loop.
 */
class CallStateListener private constructor(private val appContext: Context) {

    private val callStateMonitor = CallStateMonitor(appContext)
    private var ownedTts: TextToSpeechManager? = null

    fun onIncomingCall(phoneNumber: String?) {
        if (!callStateMonitor.shouldAnnounce(phoneNumber)) {
            Logger.d(TAG, "Duplicate ringing broadcast suppressed")
            return
        }
        val announcement = callStateMonitor.buildCallAnnouncement(phoneNumber)
        Logger.i(TAG, "Announcing incoming call")
        resolveTts().speak(announcement)
    }

    fun onCallEnded() {
        callStateMonitor.resetDebounce()
    }

    private fun resolveTts(): TextToSpeechManager {
        VoiceActivationService.activeTextToSpeechManager?.let { return it }
        val existing = ownedTts
        if (existing != null) return existing
        val created = TextToSpeechManager(appContext)
        ownedTts = created
        return created
    }

    companion object {
        private const val TAG = "CallStateListener"

        @Volatile
        private var instance: CallStateListener? = null

        fun getInstance(context: Context): CallStateListener {
            return instance ?: synchronized(this) {
                instance ?: CallStateListener(context.applicationContext).also { instance = it }
            }
        }
    }
}
