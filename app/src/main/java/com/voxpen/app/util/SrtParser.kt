package com.voxpen.app.util

import com.voxpen.app.data.repository.TranscriptionSegment

/**
 * Parses SRT subtitle files into segments (desktop parity: srt.rs parse_srt).
 *
 * Tolerates: optional index lines, BOM, CRLF/CR line endings, ',' or '.' fraction
 * separators, 1–3 digit fractions, trailing position coordinates after the end
 * timestamp, and multi-line cue text. Cues with blank text are skipped.
 */
object SrtParser {
    fun parse(content: String): Result<List<TranscriptionSegment>> =
        runCatching {
            val normalized =
                content
                    .removePrefix("\uFEFF")
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
            val lines = normalized.lines()
            val segments = mutableListOf<TranscriptionSegment>()

            var i = 0
            while (i < lines.size) {
                while (i < lines.size && lines[i].isBlank()) i++
                if (i >= lines.size) break

                val (timingLine, timingIdx) = resolveTimingLine(lines, i)
                val (startMs, endMs) = parseTimingLine(timingLine)
                i = timingIdx + 1


                val textLines = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank()) {
                    textLines.add(lines[i].trimEnd())
                    i++
                }
                val text = textLines.joinToString("\n").trim()
                if (text.isNotEmpty()) {
                    segments.add(TranscriptionSegment(startMs, endMs, text))
                }
            }

            if (segments.isEmpty()) error("SRT file contains no subtitle cues")
            segments
        }


    /** Resolves the timing line at [start], tolerating an optional index line before it. */
    private fun resolveTimingLine(
        lines: List<String>,
        start: Int,
    ): Pair<String, Int> {
        var i = start
        return when {
            lines[i].contains("-->") -> lines[i] to i
            isIndexLine(lines[i]) -> {
                i++
                while (i < lines.size && lines[i].isBlank()) i++
                if (i >= lines.size) error("SRT cue missing timing line after index")
                val next = lines[i]
                if (!next.contains("-->")) {
                    error("expected SRT timing line after index, got: $next")
                }
                next to i
            }
            else -> error("expected SRT index or timing line, got: ${lines[i]}")
        }
    }
    private fun isIndexLine(line: String): Boolean = line.isNotEmpty() && line.all { it in '0'..'9' }

    private fun parseTimingLine(line: String): Pair<Long, Long> {
        val trimmed = line.trim()
        val arrow = trimmed.indexOf("-->")
        if (arrow < 0) error("invalid SRT timing line: $line")
        val startPart = trimmed.substring(0, arrow).trim()
        val endPart =
            trimmed.substring(arrow + 3).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        return parseTimestamp(startPart) to parseTimestamp(endPart)
    }

    private fun parseTimestamp(ts: String): Long {
        val sep = ts.indexOfFirst { it == ',' || it == '.' }
        if (sep < 0) error("invalid SRT timestamp (missing fraction): $ts")
        val timePart = ts.substring(0, sep)
        val fractionMs =
            ts.substring(sep + 1)
                .take(3)
                .padEnd(3, '0')
                .toIntOrNull()
                ?: error("invalid SRT timestamp: $ts")
        val parts = timePart.split(":")
        if (parts.size != 3) error("invalid SRT timestamp (expected h:m:s): $ts")
        val h = parts[0].toLongOrNull() ?: error("invalid SRT timestamp hours: $ts")
        val m = parts[1].toLongOrNull() ?: error("invalid SRT timestamp minutes: $ts")
        val s = parts[2].toLongOrNull() ?: error("invalid SRT timestamp seconds: $ts")
        return (h * 3600 + m * 60 + s) * 1000 + fractionMs
    }
}
