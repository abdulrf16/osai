package com.osai.voiceassistant.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.osai.voiceassistant.audio.TextToSpeechManager
import com.osai.voiceassistant.notifications.IncomingMessage
import com.osai.voiceassistant.notifications.MessageNotificationHelper
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.utils.Logger
import java.util.Collections
import kotlinx.coroutines.delay

/**
 * Listens for WhatsApp / SMS / Telegram notifications and buffers them so
 * they can be read aloud on demand ("Read my messages"). Also doubles as the
 * bound NotificationListenerService component required by
 * [android.media.session.MediaSessionManager.getActiveSessions] for media
 * control.
 *
 * Message bodies are held only in memory (never logged, never persisted) and
 * are cleared once read aloud.
 */
class NotificationMessageReader : NotificationListenerService() {

    private val bufferedMessages = Collections.synchronizedList(mutableListOf<IncomingMessage>())

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        Logger.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeInstance === this) activeInstance = null
        Logger.i(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) activeInstance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        try {
            val allowed = SharedPreferencesManager.getInstance(applicationContext)
                .current().enabledMessagingPackages
            if (!MessageNotificationHelper.isSupportedMessagingApp(sbn.packageName, allowed)) return

            val message = MessageNotificationHelper.extractMessage(sbn) ?: return
            synchronized(bufferedMessages) {
                bufferedMessages.add(message)
                if (bufferedMessages.size > MAX_BUFFERED_MESSAGES) {
                    bufferedMessages.removeAt(0)
                }
            }
            Logger.d(TAG, "Buffered a new message notification (count=${bufferedMessages.size})")
        } catch (e: Exception) {
            // Never let a malformed notification crash the listener service.
            Logger.e(TAG, "Failed to process notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }

    /** Snapshot + clear the buffer. Called when the user says "read my messages". */
    fun drainMessages(): List<IncomingMessage> {
        synchronized(bufferedMessages) {
            val snapshot = bufferedMessages.toList()
            bufferedMessages.clear()
            return snapshot
        }
    }

    fun hasUnreadMessages(): Boolean = bufferedMessages.isNotEmpty()

    companion object {
        private const val TAG = "NotificationMessageReader"
        private const val MAX_BUFFERED_MESSAGES = 20

        @Volatile
        private var activeInstance: NotificationMessageReader? = null

        /**
         * Reads all buffered messages aloud via [tts], or announces that
         * there are none. Safe to call even if the listener service has not
         * connected yet (e.g. permission not granted) - it degrades to a
         * spoken explanation rather than crashing.
         */
        suspend fun readUnreadMessages(tts: TextToSpeechManager) {
            val instance = activeInstance
            if (instance == null) {
                tts.speakAndAwait("Message reading is not available. Please enable notification access in settings.")
                return
            }
            val messages = instance.drainMessages()
            if (messages.isEmpty()) {
                tts.speakAndAwait(MessageNotificationHelper.formatSummaryForSpeech(0))
                return
            }
            tts.speakAndAwait(MessageNotificationHelper.formatSummaryForSpeech(messages.size))
            for (message in messages) {
                delay(150)
                tts.speakAndAwait(MessageNotificationHelper.formatForSpeech(message), timeoutMs = 12000L)
            }
        }

        fun isListenerConnected(): Boolean = activeInstance != null
    }
}
