package com.osai.voiceassistant.settings

/**
 * Feedback verbosity levels for spoken confirmations. Kept simple for
 * MVP 1: FULL speaks every confirmation, MINIMAL only speaks
 * errors/important events (incoming calls, messages), SILENT disables
 * spoken confirmations entirely (media/system sounds still play).
 */
enum class FeedbackVerbosity {
    FULL,
    MINIMAL,
    SILENT
}

/**
 * Immutable snapshot of user-configurable settings, persisted via
 * [SharedPreferencesManager]. Pure data class - no Android dependency -
 * so it's trivial to construct in tests.
 */
data class AppSettings(
    val wakePhrase: String = "Hey Assistant",
    val feedbackVerbosity: FeedbackVerbosity = FeedbackVerbosity.FULL,
    val enabledMessagingPackages: Set<String> = DEFAULT_MESSAGING_PACKAGES,
    val ridingModeEnabled: Boolean = false,
    val hasCompletedPermissionWizard: Boolean = false
) {
    companion object {
        val DEFAULT_MESSAGING_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging"
        )
    }
}
