package com.osai.voiceassistant.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNotificationHelperTest {

    @Test
    fun `whatsapp package is supported when in allow list`() {
        val allowed = setOf("com.whatsapp", "org.telegram.messenger")
        assertTrue(MessageNotificationHelper.isSupportedMessagingApp("com.whatsapp", allowed))
    }

    @Test
    fun `unrelated package is not supported`() {
        val allowed = setOf("com.whatsapp", "org.telegram.messenger")
        assertFalse(MessageNotificationHelper.isSupportedMessagingApp("com.instagram.android", allowed))
    }

    @Test
    fun `empty allow list supports nothing`() {
        assertFalse(MessageNotificationHelper.isSupportedMessagingApp("com.whatsapp", emptySet()))
    }

    @Test
    fun `formatForSpeech includes sender and body`() {
        val message = IncomingMessage(
            appPackage = "com.whatsapp",
            sender = "Alex",
            body = "See you at 5",
            postTimeMillis = 0L
        )
        assertEquals("Message from Alex: See you at 5", MessageNotificationHelper.formatForSpeech(message))
    }

    @Test
    fun `formatSummaryForSpeech handles zero one and many`() {
        assertEquals("You have no new messages", MessageNotificationHelper.formatSummaryForSpeech(0))
        assertEquals("You have one new message", MessageNotificationHelper.formatSummaryForSpeech(1))
        assertEquals("You have 3 new messages", MessageNotificationHelper.formatSummaryForSpeech(3))
    }
}
