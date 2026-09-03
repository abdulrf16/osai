package com.osai.voiceassistant.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRoutingPolicyTest {

    @Test
    fun `mic source prefers bluetooth headset when connected`() {
        assertEquals(MicSource.BLUETOOTH_HEADSET, AudioRoutingPolicy.decideMicSource(bluetoothHeadsetConnected = true))
    }

    @Test
    fun `mic source falls back to phone mic when no headset connected`() {
        assertEquals(MicSource.PHONE_MIC, AudioRoutingPolicy.decideMicSource(bluetoothHeadsetConnected = false))
    }

    @Test
    fun `output sink prefers bluetooth headset when connected`() {
        assertEquals(OutputSink.BLUETOOTH_HEADSET, AudioRoutingPolicy.decideOutputSink(bluetoothHeadsetConnected = true))
    }

    @Test
    fun `output sink falls back to phone speaker when no headset connected`() {
        assertEquals(OutputSink.PHONE_SPEAKER, AudioRoutingPolicy.decideOutputSink(bluetoothHeadsetConnected = false))
    }
}
