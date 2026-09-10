package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.repository.TranscriptionSegment
import com.voxpen.app.util.ExportHelper
import com.voxpen.app.util.SrtParser
import javax.inject.Inject

data class SrtImportResult(
    val fileName: String,
    /** Canonically reformatted SRT of the original cues. */
    val originalSrt: String,
    /** SRT with refined cue text; timestamps unchanged. */
    val refinedSrt: String,
    val originalText: String,
    val refinedText: String,
)

/**
 * Refine an existing SRT file: parse cues → per-segment LLM refine → new SRT (desktop parity:
 * refine_srt_file). Always runs segment refine — the user explicitly requested this action.
 * Errors propagate; nothing is persisted (results live in UI state until shared).
 */
class ImportSrtUseCase
    @Inject
    constructor(
        private val refineSegmentsUseCase: RefineSegmentsUseCase,
    ) {
        suspend operator fun invoke(
            content: String,
            fileName: String,
            language: SttLanguage,
            apiKey: String,
            model: String = "llama-3.3-70b-versatile",
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
            tone: ToneStyle = ToneStyle.Casual,
            vocabulary: List<String> = emptyList(),
            customPrompt: String? = null,
        ): Result<SrtImportResult> {
            if (content.toByteArray().size > MAX_SRT_BYTES) {
                return Result.failure(IllegalArgumentException("SRT file too large (max 5 MB)."))
            }
            val segments =
                SrtParser.parse(content).getOrElse {
                    return Result.failure(IllegalArgumentException("Invalid SRT: ${it.message}"))
                }
            val refined =
                refineSegmentsUseCase(
                    segments, language, apiKey, model, vocabulary, customPrompt, tone, provider, customBaseUrl,
                ).getOrElse { return Result.failure(it) }
            return Result.success(
                SrtImportResult(
                    fileName = fileName,
                    originalSrt = ExportHelper.segmentsToSrt(segments),
                    refinedSrt = ExportHelper.segmentsToSrt(refined),
                    originalText = ExportHelper.segmentsToText(segments),
                    refinedText = ExportHelper.segmentsToText(refined),
                ),
            )
        }

        companion object {
            private const val MAX_SRT_BYTES = 5 * 1024 * 1024
        }
    }
