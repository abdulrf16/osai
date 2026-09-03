package com.osai.voiceassistant.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    private val parser = CommandParser()

    @Test
    fun `play synonyms map to PLAY`() {
        listOf("play", "play music", "play song", "start music").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.PLAY, parser.parse(phrase).type)
        }
    }

    @Test
    fun `pause synonyms map to PAUSE`() {
        listOf("pause", "pause music", "pause song", "hold on").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.PAUSE, parser.parse(phrase).type)
        }
    }

    @Test
    fun `resume synonyms map to RESUME`() {
        listOf("resume", "resume music", "continue", "continue playing", "unpause").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.RESUME, parser.parse(phrase).type)
        }
    }

    @Test
    fun `next track synonyms map to NEXT_TRACK`() {
        listOf("next", "next song", "skip", "skip track", "forward").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.NEXT_TRACK, parser.parse(phrase).type)
        }
    }

    @Test
    fun `previous track synonyms map to PREVIOUS_TRACK`() {
        listOf("previous", "previous song", "go back", "back", "rewind").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.PREVIOUS_TRACK, parser.parse(phrase).type)
        }
    }

    @Test
    fun `stop synonyms map to STOP`() {
        listOf("stop", "stop music", "stop playing").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.STOP, parser.parse(phrase).type)
        }
    }

    @Test
    fun `volume up synonyms map to VOLUME_UP`() {
        listOf("volume up", "turn up the volume", "increase volume", "louder", "turn it up").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.VOLUME_UP, parser.parse(phrase).type)
        }
    }

    @Test
    fun `volume down synonyms map to VOLUME_DOWN`() {
        listOf("volume down", "turn down the volume", "decrease volume", "quieter", "lower the volume").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.VOLUME_DOWN, parser.parse(phrase).type)
        }
    }

    @Test
    fun `read messages synonyms map to READ_MESSAGES`() {
        listOf("read my messages", "read messages", "check messages", "any messages").forEach { phrase ->
            assertEquals("phrase=$phrase", CommandType.READ_MESSAGES, parser.parse(phrase).type)
        }
    }

    @Test
    fun `set volume to explicit level is parsed with correct level`() {
        val result = parser.parse("set volume to 50")
        assertEquals(CommandType.SET_VOLUME_LEVEL, result.type)
        assertEquals(50, result.level)
    }

    @Test
    fun `set the volume to phrasing is parsed`() {
        val result = parser.parse("set the volume to 80")
        assertEquals(CommandType.SET_VOLUME_LEVEL, result.type)
        assertEquals(80, result.level)
    }

    @Test
    fun `volume percent phrasing is parsed`() {
        val result = parser.parse("volume 30 percent")
        assertEquals(CommandType.SET_VOLUME_LEVEL, result.type)
        assertEquals(30, result.level)
    }

    @Test
    fun `level above 100 is clamped`() {
        val result = parser.parse("set volume to 150")
        assertEquals(CommandType.SET_VOLUME_LEVEL, result.type)
        assertEquals(100, result.level)
    }

    @Test
    fun `unrecognized phrase maps to UNKNOWN`() {
        val result = parser.parse("what's the weather today")
        assertEquals(CommandType.UNKNOWN, result.type)
        assertNull(result.level)
    }

    @Test
    fun `blank input maps to UNKNOWN`() {
        assertEquals(CommandType.UNKNOWN, parser.parse("").type)
        assertEquals(CommandType.UNKNOWN, parser.parse("   ").type)
    }

    @Test
    fun `parsing is case insensitive and tolerant of punctuation`() {
        assertEquals(CommandType.NEXT_TRACK, parser.parse("Next, please!").type)
        assertEquals(CommandType.PLAY, parser.parse("PLAY").type)
    }

    @Test
    fun `word boundary prevents partial word matches`() {
        // "back" should not match inside "background" or "backpack" if such
        // words were ever spoken as part of an unrelated sentence.
        val result = parser.parse("turn on the background music service")
        // "background" contains "back" but should not spuriously trigger
        // PREVIOUS_TRACK due to the word boundary check.
        assertEquals(CommandType.UNKNOWN, result.type)
    }

    @Test
    fun `raw text is preserved on the parsed command`() {
        val raw = "Play Music"
        val result = parser.parse(raw)
        assertEquals(raw, result.rawText)
    }
}
