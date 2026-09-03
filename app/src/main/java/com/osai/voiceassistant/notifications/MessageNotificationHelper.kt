package com.osai.voiceassistant.notifications

import android.app.Notification
import android.os.Parcelable
import android.service.notification.StatusBarNotification

/** Domain model for a message extracted from a notification. */
data class IncomingMessage(
    val appPackage: String,
    val sender: String,
    val body: String,
    val postTimeMillis: Long
)

/**
 * Extracts sender/body from WhatsApp, SMS, and Telegram style notifications
 * and formats them for TTS. Filtering logic ([isSupportedMessagingApp]) is
 * pure and unit tested; extraction requires the Android notification APIs
 * and is exercised via instrumented tests / manual verification.
 */
object MessageNotificationHelper {

    /** Pure filter - testable without any Android framework classes. */
    fun isSupportedMessagingApp(packageName: String, allowedPackages: Set<String>): Boolean {
        return packageName in allowedPackages
    }

    fun extractMessage(sbn: StatusBarNotification): IncomingMessage? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // MessagingStyle notifications (used by WhatsApp/Telegram) expose the
        // most recent message + sender via EXTRA_MESSAGES, which is more
        // reliable than the summary title/text for group chats.
        val messagingSender = extractLatestMessagingStyleSender(sbn)
        val messagingBody = extractLatestMessagingStyleBody(sbn)

        val sender = messagingSender ?: title ?: return null
        val body = messagingBody ?: text ?: return null

        if (body.isBlank()) return null

        return IncomingMessage(
            appPackage = sbn.packageName,
            sender = sender,
            body = body,
            postTimeMillis = sbn.postTime
        )
    }

    fun formatForSpeech(message: IncomingMessage): String {
        return "Message from ${message.sender}: ${message.body}"
    }

    fun formatSummaryForSpeech(count: Int): String {
        return when (count) {
            0 -> "You have no new messages"
            1 -> "You have one new message"
            else -> "You have $count new messages"
        }
    }

    @Suppress("DEPRECATION")
    private fun extractLatestMessagingStyleSender(sbn: StatusBarNotification): String? {
        return try {
            val extras = sbn.notification.extras
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) as? Array<Parcelable>
            val last = messages?.lastOrNull() ?: return null
            val bundle = last as? android.os.Bundle ?: return null
            bundle.getCharSequence("sender")?.toString()
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun extractLatestMessagingStyleBody(sbn: StatusBarNotification): String? {
        return try {
            val extras = sbn.notification.extras
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) as? Array<Parcelable>
            val last = messages?.lastOrNull() ?: return null
            val bundle = last as? android.os.Bundle ?: return null
            bundle.getCharSequence("text")?.toString()
        } catch (e: Exception) {
            null
        }
    }
}
