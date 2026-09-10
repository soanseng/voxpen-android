package com.voxpen.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SrtParserTest {
    @Test
    fun `should parse standard cues with index lines`() {
        val content =
            """
            1
            00:00:01,000 --> 00:00:02,500
            Hello world

            2
            00:00:03,000 --> 00:00:04,000
            Second cue
            """.trimIndent()

        val result = SrtParser.parse(content)

        assertThat(result.isSuccess).isTrue()
        val segments = result.getOrThrow()
        assertThat(segments).hasSize(2)
        assertThat(segments[0].startMs).isEqualTo(1000)
        assertThat(segments[0].endMs).isEqualTo(2500)
        assertThat(segments[0].text).isEqualTo("Hello world")
        assertThat(segments[1].startMs).isEqualTo(3000)
        assertThat(segments[1].text).isEqualTo("Second cue")
    }

    @Test
    fun `should parse cues without index lines`() {
        val content =
            """
            00:00:00,000 --> 00:00:01,000
            No index here
            """.trimIndent()

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments).hasSize(1)
        assertThat(segments[0].text).isEqualTo("No index here")
    }

    @Test
    fun `should handle BOM and CRLF line endings`() {
        val content = "﻿1\r\n00:00:01,000 --> 00:00:02,000\r\nCRLF cue\r\n"

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments).hasSize(1)
        assertThat(segments[0].text).isEqualTo("CRLF cue")
    }

    @Test
    fun `should accept dot fraction separator and short fractions`() {
        val content =
            """
            00:00:01.5 --> 00:00:02.50
            Dot millis
            """.trimIndent()

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments[0].startMs).isEqualTo(1500)
        assertThat(segments[0].endMs).isEqualTo(2500)
    }

    @Test
    fun `should ignore trailing position coordinates after end timestamp`() {
        val content =
            """
            00:00:01,000 --> 00:00:02,000 X1:0 Y1:0 X2:100 Y2:50
            Positioned cue
            """.trimIndent()

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments[0].endMs).isEqualTo(2000)
    }

    @Test
    fun `should join multi-line cue text`() {
        val content =
            """
            1
            00:00:01,000 --> 00:00:02,000
            First line
            Second line

            2
            00:00:03,000 --> 00:00:04,000
            After multi-line
            """.trimIndent()

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments[0].text).isEqualTo("First line\nSecond line")
        assertThat(segments[1].text).isEqualTo("After multi-line")
    }

    @Test
    fun `should skip cues with blank text`() {
        val content =
            """
            1
            00:00:01,000 --> 00:00:02,000

            2
            00:00:03,000 --> 00:00:04,000
            Kept cue
            """.trimIndent()

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments).hasSize(1)
        assertThat(segments[0].text).isEqualTo("Kept cue")
    }

    @Test
    fun `should fail when file has no cues`() {
        val result = SrtParser.parse("")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("no subtitle cues")
    }

    @Test
    fun `should fail on garbage first line`() {
        val result = SrtParser.parse("not a cue at all\n00:00:01,000 --> 00:00:02,000\ntext")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("expected SRT index or timing line")
    }

    @Test
    fun `should fail when timing line missing after index`() {
        val result = SrtParser.parse("1\n2\ntext")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("timing line after index")
    }

    @Test
    fun `should fail when timestamp has no fraction separator`() {
        val result = SrtParser.parse("00:00:01 --> 00:00:02,000\nNo fraction")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("missing fraction")
    }

    @Test
    fun `should parse hours beyond 24`() {
        val content =
            """
            01:30:00,000 --> 01:30:05,000
            Long video cue
            """.trimIndent()

        val segments = SrtParser.parse(content).getOrThrow()

        assertThat(segments[0].startMs).isEqualTo((1 * 3600 + 30 * 60) * 1000)
    }
}
