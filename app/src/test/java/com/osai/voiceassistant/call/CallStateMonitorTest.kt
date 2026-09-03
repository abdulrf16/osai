package com.osai.voiceassistant.call

import org.junit.Assert.assertEquals
import org.junit.Test

class CallStateMonitorTest {

    @Test
    fun `announcement uses contact name when available`() {
        val announcement = CallStateMonitor.buildAnnouncement(phoneNumber = "5551234", contactName = "Alex")
        assertEquals("Incoming call from Alex", announcement)
    }

    @Test
    fun `announcement falls back to phone number when no contact name`() {
        val announcement = CallStateMonitor.buildAnnouncement(phoneNumber = "5551234", contactName = null)
        assertEquals("Incoming call from 5551234", announcement)
    }

    @Test
    fun `announcement falls back to generic phrase when nothing is known`() {
        val announcement = CallStateMonitor.buildAnnouncement(phoneNumber = null, contactName = null)
        assertEquals("Incoming call from unknown number", announcement)
    }

    @Test
    fun `blank contact name is treated as missing`() {
        val announcement = CallStateMonitor.buildAnnouncement(phoneNumber = "5551234", contactName = "  ")
        assertEquals("Incoming call from 5551234", announcement)
    }
}
