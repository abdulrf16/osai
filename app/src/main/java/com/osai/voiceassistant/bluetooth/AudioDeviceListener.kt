package com.osai.voiceassistant.bluetooth

/** Where microphone input should be captured from. */
enum class MicSource {
    BLUETOOTH_HEADSET,
    PHONE_MIC
}

/** Where TTS/audio feedback should be routed to. */
enum class OutputSink {
    BLUETOOTH_HEADSET,
    PHONE_SPEAKER
}

/**
 * Pure decision logic for audio routing priority (Bluetooth headset first,
 * phone mic/speaker as fallback). Deliberately free of any Android
 * dependency so it can be unit tested exhaustively without mocking
 * [android.bluetooth] or [android.media] classes.
 */
object AudioRoutingPolicy {

    fun decideMicSource(bluetoothHeadsetConnected: Boolean): MicSource {
        return if (bluetoothHeadsetConnected) MicSource.BLUETOOTH_HEADSET else MicSource.PHONE_MIC
    }

    fun decideOutputSink(bluetoothHeadsetConnected: Boolean): OutputSink {
        return if (bluetoothHeadsetConnected) OutputSink.BLUETOOTH_HEADSET else OutputSink.PHONE_SPEAKER
    }
}
