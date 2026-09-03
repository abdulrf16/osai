package com.osai.voiceassistant.command

/**
 * Deterministic, keyword-based parser for MVP 1. No ML/LLM involved -
 * intentionally simple so behaviour is predictable and easy to test.
 *
 * Pure Kotlin (no Android imports) so it can be unit tested on the plain
 * JVM without instrumentation.
 */
class CommandParser {

    fun parse(rawText: String): ParsedCommand {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return ParsedCommand(CommandType.UNKNOWN, rawText = rawText)
        }

        // Check "set volume to N" style phrases first since they need extra
        // numeric extraction and would otherwise be swallowed by the plain
        // "volume" synonym matching below.
        extractVolumeLevel(normalized)?.let { level ->
            return ParsedCommand(CommandType.SET_VOLUME_LEVEL, level = level, rawText = rawText)
        }

        for ((phrase, command) in CommandSynonyms.PHRASE_TO_COMMAND) {
            if (containsPhrase(normalized, phrase)) {
                return ParsedCommand(command, rawText = rawText)
            }
        }

        return ParsedCommand(CommandType.UNKNOWN, rawText = rawText)
    }

    private fun extractVolumeLevel(normalized: String): Int? {
        for (prefix in CommandSynonyms.SET_VOLUME_PREFIXES) {
            if (normalized.contains(prefix)) {
                val afterPrefix = normalized.substringAfter(prefix).trim()
                val digits = Regex("\\d+").find(afterPrefix) ?: continue
                val value = digits.value.toIntOrNull() ?: continue
                return value.coerceIn(0, 100)
            }
        }
        // Also support bare "volume <number> percent"
        val match = Regex("volume\\s+(\\d{1,3})\\s*(percent)?").find(normalized)
        if (match != null) {
            val value = match.groupValues[1].toIntOrNull() ?: return null
            return value.coerceIn(0, 100)
        }
        return null
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        // Word-boundary match so "back" doesn't match inside "background".
        val pattern = Regex("(^|\\s)${Regex.escape(phrase)}($|\\s)")
        return pattern.containsMatchIn(normalized)
    }

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
    }
}
