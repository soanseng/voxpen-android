package com.voxpen.app.ime

import com.voxpen.app.billing.ProStatus
import com.voxpen.app.billing.UsageLimiter
import com.voxpen.app.data.local.ApiKeyManager
import com.voxpen.app.data.local.PreferencesManager
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.ToneStyle
import com.voxpen.app.data.repository.DictionaryRepository
import com.voxpen.app.domain.usecase.RefineTextUseCase
import com.voxpen.app.domain.usecase.TranscribeAudioUseCase
import com.voxpen.app.util.VocabularyPromptBuilder
import com.voxpen.app.data.model.VoiceCommand
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecordingController(
    private val transcribeUseCase: TranscribeAudioUseCase,
    private val refineTextUseCase: RefineTextUseCase,
    private val apiKeyManager: ApiKeyManager,
    private val preferencesManager: PreferencesManager,
    private val dictionaryRepository: DictionaryRepository,
    private val usageLimiter: UsageLimiter,
    private val proStatusProvider: () -> ProStatus,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var refinementEnabled: Boolean = PreferencesManager.DEFAULT_REFINEMENT_ENABLED
    private var sttModel: String = PreferencesManager.DEFAULT_STT_MODEL
    private var llmModel: String = PreferencesManager.DEFAULT_LLM_MODEL
    private var toneStyle: ToneStyle = ToneStyle.DEFAULT
    private var llmProvider: LlmProvider = LlmProvider.DEFAULT
    private var customLlmModel: String = ""
    private var customSttBaseUrl: String = ""
    private var translationEnabled: Boolean = PreferencesManager.DEFAULT_TRANSLATION_ENABLED
    private var translationTargetLanguage: SttLanguage = PreferencesManager.DEFAULT_TRANSLATION_TARGET_LANGUAGE

    init {
        scope.launch {
            preferencesManager.refinementEnabledFlow.collect { refinementEnabled = it }
        }
        scope.launch { preferencesManager.sttModelFlow.collect { sttModel = it } }
        scope.launch { preferencesManager.llmModelFlow.collect { llmModel = it } }
        scope.launch { preferencesManager.toneStyleFlow.collect { toneStyle = it } }
        scope.launch { preferencesManager.llmProviderFlow.collect { llmProvider = it } }
        scope.launch { preferencesManager.customLlmModelFlow.collect { customLlmModel = it } }
        scope.launch { preferencesManager.customSttBaseUrlFlow.collect { customSttBaseUrl = it } }
        scope.launch { preferencesManager.translationEnabledFlow.collect { translationEnabled = it } }
        scope.launch { preferencesManager.translationTargetLanguageFlow.collect { translationTargetLanguage = it } }
    }

    private val _uiState = MutableStateFlow<ImeUiState>(ImeUiState.Idle)
    val uiState: StateFlow<ImeUiState> = _uiState.asStateFlow()

    fun onStartRecording(startRecording: () -> Unit) {
        val proStatus = proStatusProvider()
        if (!proStatus.isPro && !usageLimiter.canUseVoiceInput()) {
            val remaining = usageLimiter.remainingVoiceInputs()
            _uiState.value =
                ImeUiState.Error("Daily limit reached ($remaining remaining). Upgrade to Pro for unlimited use.")
            return
        }
        startRecording()
        _uiState.value = ImeUiState.Recording
    }

    fun onStopRecording(
        stopRecording: () -> ByteArray,
        language: SttLanguage,
        editMode: Boolean = false,
    ) {
        val pcmData = stopRecording()
        val apiKey = apiKeyManager.getApiKey(llmProvider)
            ?: apiKeyManager.getGroqApiKey()  // fallback to Groq key for backward compat

        if (apiKey.isNullOrBlank()) {
            _uiState.value = ImeUiState.Error("API key not configured")
            return
        }

        _uiState.value = ImeUiState.Processing
        scope.launch {
            val proStatus = proStatusProvider()
            val vocabulary = dictionaryRepository.getWords(80)
            val whisperPrompt =
                if (vocabulary.isNotEmpty()) {
                    VocabularyPromptBuilder.buildWhisperPrompt(language, vocabulary)
                } else {
                    null
                }
            val sttBaseUrl = customSttBaseUrl.ifBlank { null }
            val result = transcribeUseCase(pcmData, language, apiKey, sttModel, vocabularyHint = whisperPrompt, customSttBaseUrl = sttBaseUrl)
            result.fold(
                onSuccess = { originalText ->
                    if (!proStatus.isPro) {
                        usageLimiter.incrementVoiceInput()
                    }

                    // Speak-to-Edit: emit instruction for VoxPenIME to handle
                    if (editMode) {
                        _uiState.value = ImeUiState.EditInstruction(originalText)
                        return@launch
                    }

                    // Voice command check — executes keyboard action instead of inserting text
                    val command = VoiceCommandRecognizer.recognize(originalText)
                    if (command != null) {
                        _uiState.value = ImeUiState.CommandDetected(command)
                        return@launch
                    }

                    val shouldRefine = refinementEnabled && canUseRefinement(proStatus)
                    if (!shouldRefine) {
                        _uiState.value = ImeUiState.Result(originalText)
                        return@launch
                    }
                    _uiState.value = ImeUiState.Refining(originalText)
                    if (!proStatus.isPro) {
                        usageLimiter.incrementRefinement()
                    }
                    val allVocabulary = dictionaryRepository.getWords(500)
                    val langKey = PreferencesManager.languageToKey(language)
                    val customPrompt = preferencesManager.customPromptFlow(langKey).first()
                    val resolvedModel = if (llmProvider == LlmProvider.Custom) {
                        customLlmModel.ifBlank { llmModel }
                    } else {
                        llmModel
                    }
                    val customBaseUrl = if (llmProvider == LlmProvider.Custom) {
                        apiKeyManager.getCustomBaseUrl()
                    } else {
                        null
                    }
                    val refinedResult = refineTextUseCase(
                        originalText, language, apiKey, resolvedModel, allVocabulary,
                        customPrompt, toneStyle, llmProvider, customBaseUrl,
                        translationEnabled, translationTargetLanguage,
                    )
                    _uiState.value =
                        refinedResult.fold(
                            onSuccess = { ImeUiState.Refined(originalText, it) },
                            onFailure = { ImeUiState.Result(originalText) },
                        )
                },
                onFailure = {
                    _uiState.value = ImeUiState.Error(it.message ?: "Transcription failed")
                },
            )
        }
    }

    private fun canUseRefinement(proStatus: ProStatus): Boolean = proStatus.isPro || usageLimiter.canUseRefinement()

    fun dismiss() {
        _uiState.value = ImeUiState.Idle
    }

    fun destroy() {
        scope.cancel()
    }
}
