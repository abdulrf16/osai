package com.osai.voiceassistant.utils

/**
 * Central place for magic strings/numbers shared across packages so they
 * don't drift out of sync between the service, UI, and receivers.
 */
object Constants {

    // Wake word (hardcoded for MVP 1 - custom phrases are MVP 2 scope)
    const val WAKE_WORD_PHRASE = "Hey Assistant"
    const val PORCUPINE_MODEL_ASSET_PATH = "porcupine/hey_assistant.ppn"

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "riding_assistant_service"
    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val MESSAGE_READ_NOTIFICATION_ID = 1002

    // Service actions
    const val ACTION_START_RIDING_MODE = "com.osai.voiceassistant.action.START_RIDING_MODE"
    const val ACTION_STOP_RIDING_MODE = "com.osai.voiceassistant.action.STOP_RIDING_MODE"
    const val ACTION_TEST_MIC = "com.osai.voiceassistant.action.TEST_MIC"
    const val ACTION_TEST_SPEAKER = "com.osai.voiceassistant.action.TEST_SPEAKER"

    // Broadcasts emitted by the service for the UI to observe status
    const val BROADCAST_STATUS_UPDATE = "com.osai.voiceassistant.broadcast.STATUS_UPDATE"
    const val EXTRA_SERVICE_STATE = "extra_service_state"

    // Preferences
    const val PREFS_NAME = "riding_assistant_prefs"

    // Speech recognition
    const val SPEECH_RECOGNITION_TIMEOUT_MS = 6000L
    const val WAKE_WORD_REARM_DELAY_MS = 400L

    // Supported messaging apps for the notification listener (package names)
    val DEFAULT_MESSAGING_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.google.android.apps.messaging", // Google Messages (SMS)
        "com.samsung.android.messaging" // Samsung Messages (SMS)
    )
}
