package com.osai.voiceassistant.command

/**
 * Maps every phrase a rider might plausibly say to a [CommandType]. Kept as
 * plain data (no Android dependency) so it is trivial to unit test and to
 * extend with new phrases without touching parsing logic.
 */
object CommandSynonyms {

    /**
     * Ordered so that longer / more specific phrases are checked before
     * shorter, more generic ones (e.g. "turn up the volume" before "up").
     */
    val PHRASE_TO_COMMAND: Map<String, CommandType> = linkedMapOf(
        // Play
        "play music" to CommandType.PLAY,
        "play song" to CommandType.PLAY,
        "play" to CommandType.PLAY,
        "start music" to CommandType.PLAY,

        // Pause
        "pause music" to CommandType.PAUSE,
        "pause song" to CommandType.PAUSE,
        "pause" to CommandType.PAUSE,
        "hold on" to CommandType.PAUSE,

        // Resume
        "resume music" to CommandType.RESUME,
        "resume song" to CommandType.RESUME,
        "resume" to CommandType.RESUME,
        "continue music" to CommandType.RESUME,
        "continue playing" to CommandType.RESUME,
        "continue" to CommandType.RESUME,
        "unpause" to CommandType.RESUME,

        // Next
        "next song" to CommandType.NEXT_TRACK,
        "next track" to CommandType.NEXT_TRACK,
        "skip song" to CommandType.NEXT_TRACK,
        "skip track" to CommandType.NEXT_TRACK,
        "skip" to CommandType.NEXT_TRACK,
        "next" to CommandType.NEXT_TRACK,
        "forward" to CommandType.NEXT_TRACK,

        // Previous
        "previous song" to CommandType.PREVIOUS_TRACK,
        "previous track" to CommandType.PREVIOUS_TRACK,
        "last song" to CommandType.PREVIOUS_TRACK,
        "go back" to CommandType.PREVIOUS_TRACK,
        "previous" to CommandType.PREVIOUS_TRACK,
        "back" to CommandType.PREVIOUS_TRACK,
        "rewind" to CommandType.PREVIOUS_TRACK,

        // Stop
        "stop music" to CommandType.STOP,
        "stop song" to CommandType.STOP,
        "stop playing" to CommandType.STOP,
        "stop" to CommandType.STOP,

        // Volume up
        "volume up" to CommandType.VOLUME_UP,
        "turn up the volume" to CommandType.VOLUME_UP,
        "turn the volume up" to CommandType.VOLUME_UP,
        "increase volume" to CommandType.VOLUME_UP,
        "louder" to CommandType.VOLUME_UP,
        "turn it up" to CommandType.VOLUME_UP,

        // Volume down
        "volume down" to CommandType.VOLUME_DOWN,
        "turn down the volume" to CommandType.VOLUME_DOWN,
        "turn the volume down" to CommandType.VOLUME_DOWN,
        "decrease volume" to CommandType.VOLUME_DOWN,
        "quieter" to CommandType.VOLUME_DOWN,
        "turn it down" to CommandType.VOLUME_DOWN,
        "lower the volume" to CommandType.VOLUME_DOWN,

        // Read messages
        "read my messages" to CommandType.READ_MESSAGES,
        "read messages" to CommandType.READ_MESSAGES,
        "read my message" to CommandType.READ_MESSAGES,
        "check messages" to CommandType.READ_MESSAGES,
        "any messages" to CommandType.READ_MESSAGES,
        "what are my messages" to CommandType.READ_MESSAGES
    )

    /**
     * Phrases that carry an explicit numeric volume level, e.g.
     * "set volume to 50" or "volume 30 percent". The trailing digits are
     * extracted separately by [CommandParser].
     */
    val SET_VOLUME_PREFIXES: List<String> = listOf(
        "set volume to",
        "set the volume to",
        "volume to",
        "set volume",
        "volume level"
    )
}
