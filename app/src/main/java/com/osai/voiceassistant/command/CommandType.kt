package com.osai.voiceassistant.command

/**
 * The closed set of voice commands supported in MVP 1. Deliberately
 * exhaustive and flat (no LLM, no free-form intents) so that parsing stays
 * deterministic and testable.
 */
enum class CommandType {
    PLAY,
    PAUSE,
    RESUME,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    STOP,
    VOLUME_UP,
    VOLUME_DOWN,
    SET_VOLUME_LEVEL,
    READ_MESSAGES,
    UNKNOWN
}

/**
 * Result of parsing a raw transcript. [level] is only populated for
 * [CommandType.SET_VOLUME_LEVEL] (0-100 scale).
 */
data class ParsedCommand(
    val type: CommandType,
    val level: Int? = null,
    val rawText: String = ""
)
