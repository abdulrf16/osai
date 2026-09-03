package com.osai.voiceassistant.command

import com.osai.voiceassistant.audio.TextToSpeechManager
import com.osai.voiceassistant.media.AudioVolumeManager
import com.osai.voiceassistant.media.MediaControlManager
import com.osai.voiceassistant.service.NotificationMessageReader
import com.osai.voiceassistant.settings.FeedbackVerbosity
import com.osai.voiceassistant.utils.Logger

/**
 * Executes a [ParsedCommand] against the relevant Android subsystem
 * (media session, volume, message reader) and speaks a confirmation via
 * [TextToSpeechManager]. This is the single place that wires "what the user
 * said" to "what the phone does".
 */
class CommandExecutor(
    private val mediaControlManager: MediaControlManager,
    private val audioVolumeManager: AudioVolumeManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val verbosityProvider: () -> FeedbackVerbosity
) {

    suspend fun execute(command: ParsedCommand) {
        Logger.i(TAG, "Executing command: ${command.type}")
        val outcome = when (command.type) {
            CommandType.PLAY -> Outcome(mediaControlManager.play(), "Playing music", "Couldn't start playback")
            CommandType.PAUSE -> Outcome(mediaControlManager.pause(), "Paused", "Couldn't pause")
            CommandType.RESUME -> Outcome(mediaControlManager.resume(), "Resuming", "Couldn't resume")
            CommandType.NEXT_TRACK -> Outcome(mediaControlManager.next(), "Next song", "Couldn't skip track")
            CommandType.PREVIOUS_TRACK -> Outcome(mediaControlManager.previous(), "Previous song", "Couldn't go back")
            CommandType.STOP -> Outcome(mediaControlManager.stop(), "Stopped", "Couldn't stop playback")
            CommandType.VOLUME_UP -> Outcome(audioVolumeManager.volumeUp(), "Volume up", "Couldn't change volume")
            CommandType.VOLUME_DOWN -> Outcome(audioVolumeManager.volumeDown(), "Volume down", "Couldn't change volume")
            CommandType.SET_VOLUME_LEVEL -> {
                val level = command.level
                if (level == null) {
                    Outcome(false, "", "Sorry, I didn't catch the volume level")
                } else {
                    Outcome(audioVolumeManager.setVolumePercent(level), "Volume set to $level percent", "Couldn't set volume")
                }
            }
            CommandType.READ_MESSAGES -> {
                // Message reading speaks its own multi-part feedback; it
                // manages TTS directly rather than a single confirmation.
                NotificationMessageReader.readUnreadMessages(textToSpeechManager)
                return
            }
            CommandType.UNKNOWN -> Outcome(false, "", "Sorry, I didn't understand that command")
        }
        speakOutcome(outcome)
    }

    private fun speakOutcome(outcome: Outcome) {
        val verbosity = verbosityProvider()
        when (verbosity) {
            FeedbackVerbosity.SILENT -> return
            FeedbackVerbosity.MINIMAL -> if (outcome.success) return else textToSpeechManager.speak(outcome.failureMessage)
            FeedbackVerbosity.FULL -> {
                val message = if (outcome.success) outcome.successMessage else outcome.failureMessage
                textToSpeechManager.speak(message)
            }
        }
    }

    private data class Outcome(val success: Boolean, val successMessage: String, val failureMessage: String)

    companion object {
        private const val TAG = "CommandExecutor"
    }
}
