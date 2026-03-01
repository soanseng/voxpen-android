# Voice Commands + Speak to Edit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Two features: (A) Voice Commands — say "送出"/"send" to execute keyboard actions instead of inserting text; (B) Speak to Edit — select text in any app, speak an edit instruction (e.g., "讓它更正式"), LLM replaces the selection.

**Architecture:**
- **Part A (Voice Commands):** After STT, `RecordingController` checks transcribed text against `VoiceCommandRecognizer` before refinement. If a command matches, emit `ImeUiState.CommandDetected`; `VoxPenIME` executes the keyboard action.
- **Part B (Speak to Edit):** `VoxPenIME` holds `isEditMode: Boolean`. When active, `onStopRecording()` receives `editMode=true`, RecordingController emits `ImeUiState.EditInstruction(text)` instead of refining. VoxPenIME reads the selected text from `InputConnection`, calls a new `EditTextUseCase`, and commits the result.

**Tech Stack:** Kotlin, JUnit 5, MockK, Truth, Turbine (Flow testing)

---

## Key Files

| File | Role |
|------|------|
| `data/model/VoiceCommand.kt` | NEW — sealed class of executable commands |
| `ime/VoiceCommandRecognizer.kt` | NEW — maps transcribed text → `VoiceCommand?` |
| `ime/ImeUiState.kt` | Add `CommandDetected`, `EditInstruction`, `Editing`, `EditResult` states |
| `ime/RecordingController.kt` | Add voice command check + edit mode param |
| `ime/VoxPenIME.kt` | Handle new states, edit mode toggle in quick settings |
| `data/model/EditPrompt.kt` | NEW — builds edit system prompt |
| `domain/usecase/EditTextUseCase.kt` | NEW — LLM call for speak-to-edit |
| `ime/VoxPenIMEEntryPoint.kt` | Expose `EditTextUseCase` |
| `res/values/strings.xml` | New strings for edit mode UI |
| `res/values-zh-rTW/strings.xml` | Traditional Chinese translations |

---

## Part A: Voice Commands

---

### Task A1: Create `VoiceCommand` sealed class

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt`
- Create: `app/src/test/java/com/voxpen/app/data/model/VoiceCommandTest.kt`

### Step 1: Write the failing test

```kotlin
// app/src/test/java/com/voxpen/app/data/model/VoiceCommandTest.kt
package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VoiceCommandTest {
    @Test
    fun `VoiceCommand types exist`() {
        // Just verify each command can be instantiated
        val commands: List<VoiceCommand> = listOf(
            VoiceCommand.Enter,
            VoiceCommand.Backspace,
            VoiceCommand.Newline,
            VoiceCommand.Space,
        )
        assertThat(commands).hasSize(4)
    }
}
```

### Step 2: Run test to verify it fails

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.VoiceCommandTest" 2>&1 | tail -10
```
Expected: FAIL — `VoiceCommand` not found

### Step 3: Create `VoiceCommand.kt`

```kotlin
// app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt
package com.voxpen.app.data.model

import android.view.KeyEvent

sealed class VoiceCommand(val keyCode: Int) {
    /** Sends Enter / submits the text field */
    data object Enter : VoiceCommand(KeyEvent.KEYCODE_ENTER)

    /** Deletes the character before cursor */
    data object Backspace : VoiceCommand(KeyEvent.KEYCODE_DEL)

    /** Inserts a newline character (for multi-line fields) */
    data object Newline : VoiceCommand(KeyEvent.KEYCODE_ENTER)

    /** Inserts a space character */
    data object Space : VoiceCommand(KeyEvent.KEYCODE_SPACE)
}
```

### Step 4: Run test to verify it passes

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.VoiceCommandTest" 2>&1 | tail -10
```
Expected: 1 test PASS

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/model/VoiceCommand.kt \
        app/src/test/java/com/voxpen/app/data/model/VoiceCommandTest.kt
git commit -m "feat: add VoiceCommand sealed class for keyboard action commands"
```

---

### Task A2: Create `VoiceCommandRecognizer`

**Files:**
- Create: `app/src/main/java/com/voxpen/app/ime/VoiceCommandRecognizer.kt`
- Create: `app/src/test/java/com/voxpen/app/ime/VoiceCommandRecognizerTest.kt`

### Step 1: Write failing tests

```kotlin
// app/src/test/java/com/voxpen/app/ime/VoiceCommandRecognizerTest.kt
package com.voxpen.app.ime

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.VoiceCommand
import org.junit.jupiter.api.Test

class VoiceCommandRecognizerTest {
    @Test
    fun `should recognize 送出 as Enter`() {
        assertThat(VoiceCommandRecognizer.recognize("送出")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize 傳送 as Enter`() {
        assertThat(VoiceCommandRecognizer.recognize("傳送")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize send as Enter (case insensitive)`() {
        assertThat(VoiceCommandRecognizer.recognize("Send")).isEqualTo(VoiceCommand.Enter)
    }

    @Test
    fun `should recognize 刪除 as Backspace`() {
        assertThat(VoiceCommandRecognizer.recognize("刪除")).isEqualTo(VoiceCommand.Backspace)
    }

    @Test
    fun `should recognize delete as Backspace`() {
        assertThat(VoiceCommandRecognizer.recognize("delete")).isEqualTo(VoiceCommand.Backspace)
    }

    @Test
    fun `should recognize 換行 as Newline`() {
        assertThat(VoiceCommandRecognizer.recognize("換行")).isEqualTo(VoiceCommand.Newline)
    }

    @Test
    fun `should recognize new line as Newline`() {
        assertThat(VoiceCommandRecognizer.recognize("new line")).isEqualTo(VoiceCommand.Newline)
    }

    @Test
    fun `should recognize 空格 as Space`() {
        assertThat(VoiceCommandRecognizer.recognize("空格")).isEqualTo(VoiceCommand.Space)
    }

    @Test
    fun `should return null for normal text`() {
        assertThat(VoiceCommandRecognizer.recognize("你好世界")).isNull()
        assertThat(VoiceCommandRecognizer.recognize("hello world")).isNull()
        assertThat(VoiceCommandRecognizer.recognize("")).isNull()
    }

    @Test
    fun `should ignore leading and trailing whitespace`() {
        assertThat(VoiceCommandRecognizer.recognize("  送出  ")).isEqualTo(VoiceCommand.Enter)
    }
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.VoiceCommandRecognizerTest" 2>&1 | tail -10
```
Expected: FAIL — `VoiceCommandRecognizer` not found

### Step 3: Create `VoiceCommandRecognizer.kt`

```kotlin
// app/src/main/java/com/voxpen/app/ime/VoiceCommandRecognizer.kt
package com.voxpen.app.ime

import com.voxpen.app.data.model.VoiceCommand

object VoiceCommandRecognizer {
    private val COMMANDS: Map<String, VoiceCommand> = buildMap {
        // Enter / Send
        listOf("送出", "傳送", "寄出", "send", "enter", "return", "submit").forEach {
            put(it, VoiceCommand.Enter)
        }
        // Backspace / Delete
        listOf("刪除", "退格", "delete", "backspace", "erase").forEach {
            put(it, VoiceCommand.Backspace)
        }
        // Newline
        listOf("換行", "new line", "newline", "next line").forEach {
            put(it, VoiceCommand.Newline)
        }
        // Space
        listOf("空格", "space").forEach {
            put(it, VoiceCommand.Space)
        }
    }

    /** Returns a [VoiceCommand] if [text] exactly matches a known command, null otherwise. */
    fun recognize(text: String): VoiceCommand? = COMMANDS[text.trim().lowercase()]
}
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.VoiceCommandRecognizerTest" 2>&1 | tail -10
```
Expected: 10 tests PASS

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/VoiceCommandRecognizer.kt \
        app/src/test/java/com/voxpen/app/ime/VoiceCommandRecognizerTest.kt
git commit -m "feat: add VoiceCommandRecognizer for detecting keyboard action commands from speech"
```

---

### Task A3: Add `CommandDetected` to `ImeUiState` and Wire into `RecordingController`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/ImeUiState.kt`
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt`
- Modify: `app/src/test/java/com/voxpen/app/ime/ImeUiStateTest.kt`

### Step 1: Write failing test in `RecordingControllerTest`

Look at the existing test setup in `RecordingControllerTest.kt` (the `setUp()` and `controller` construction pattern). Add a new test at the end of the class:

```kotlin
@Test
fun `should emit CommandDetected when transcribed text is a voice command`() = runTest {
    // Arrange: STT returns "送出"
    val sttRepo = SttRepository(sttApiFactory)
    val transcribeUseCase = TranscribeAudioUseCase(sttRepo)
    every { sttApiFactory.create(any(), any()) } returns groqApi
    coEvery { groqApi.transcribeAudio(any(), any()) } returns WhisperResponse(text = "送出")

    controller.uiState.test {
        assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)

        controller.onStartRecording(startRecording)
        assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)

        controller.onStopRecording(stopRecording, SttLanguage.Chinese)
        assertThat(awaitItem()).isEqualTo(ImeUiState.Processing)

        val commandState = awaitItem()
        assertThat(commandState).isInstanceOf(ImeUiState.CommandDetected::class.java)
        assertThat((commandState as ImeUiState.CommandDetected).command)
            .isEqualTo(VoiceCommand.Enter)
    }
}
```

> **Note:** The existing controller construction uses a helper or `@BeforeEach`. Add the mocks for `groqApi.transcribeAudio` returning "送出". Match the exact construction pattern of the `controller` in `RecordingControllerTest`.

### Step 2: Run test to verify it fails

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest.should emit CommandDetected when transcribed text is a voice command" 2>&1 | tail -15
```
Expected: FAIL — `ImeUiState.CommandDetected` not found

### Step 3: Add `CommandDetected` to `ImeUiState`

In `ImeUiState.kt`, add after the existing states:

```kotlin
sealed interface ImeUiState {
    data object Idle : ImeUiState
    data object Recording : ImeUiState
    data object Processing : ImeUiState
    data class Result(val text: String) : ImeUiState
    data class Refining(val original: String) : ImeUiState
    data class Refined(val original: String, val refined: String) : ImeUiState
    data class Error(val message: String) : ImeUiState

    // Voice Commands
    data class CommandDetected(val command: VoiceCommand) : ImeUiState  // NEW

    // Speak to Edit (added in Part B)
    data class EditInstruction(val instruction: String) : ImeUiState    // NEW (placeholder)
    data object Editing : ImeUiState                                     // NEW (placeholder)
    data class EditResult(val revised: String) : ImeUiState             // NEW (placeholder)
}
```

> Add these all now to avoid reopening the file in Part B.

### Step 4: Add import for `VoiceCommand` to `ImeUiState.kt`

```kotlin
import com.voxpen.app.data.model.VoiceCommand
```

### Step 5: Update `RecordingController.onStopRecording()`

In `RecordingController.kt`, find the `onSuccess` block inside `onStopRecording`. Add the voice command check **after** the `usageLimiter.incrementVoiceInput()` call but **before** the refinement check:

```kotlin
onSuccess = { originalText ->
    if (!proStatus.isPro) {
        usageLimiter.incrementVoiceInput()
    }

    // NEW: check for voice command
    val command = VoiceCommandRecognizer.recognize(originalText)
    if (command != null) {
        _uiState.value = ImeUiState.CommandDetected(command)
        return@launch
    }

    val shouldRefine = refinementEnabled && canUseRefinement(proStatus)
    // ... rest unchanged ...
}
```

Add import at top of `RecordingController.kt`:
```kotlin
import com.voxpen.app.data.model.VoiceCommand
```

### Step 6: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest" 2>&1 | tail -15
```
Expected: All PASS (new test passes, existing tests unaffected)

### Step 7: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/ImeUiState.kt \
        app/src/main/java/com/voxpen/app/ime/RecordingController.kt \
        app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt
git commit -m "feat: emit CommandDetected state when transcribed text matches voice command"
```

---

### Task A4: Handle `CommandDetected` in `VoxPenIME`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`

This is IME-only code — no unit tests possible. Verify manually.

### Step 1: Add `CommandDetected` case to `updateCandidateBar()`

In `VoxPenIME.kt`, find `updateCandidateBar(state: ImeUiState)`. Add the new case:

```kotlin
is ImeUiState.CommandDetected -> {
    timerHandler.removeCallbacks(timerRunnable)
    executeVoiceCommand(state.command)
    recordingController.dismiss()
}
```

### Step 2: Implement `executeVoiceCommand()`

Add this new private function to `VoxPenIME`:

```kotlin
private fun executeVoiceCommand(command: VoiceCommand) {
    when (command) {
        VoiceCommand.Enter -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_ENTER)
        VoiceCommand.Backspace -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
        VoiceCommand.Newline -> currentInputConnection?.commitText("\n", 1)
        VoiceCommand.Space -> currentInputConnection?.commitText(" ", 1)
    }
}
```

Add import at top of `VoxPenIME.kt`:
```kotlin
import com.voxpen.app.data.model.VoiceCommand
```

### Step 3: Build to verify

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

### Step 4: Manual test

1. Activate VoxPen keyboard in a text field
2. Tap mic → say "送出" → verify Enter key is sent (message sends or form submits)
3. Type some text → tap mic → say "刪除" → verify last character is deleted
4. Tap mic → say "換行" → verify newline is inserted (in multi-line field)
5. Tap mic → say "send" → verify Enter is sent

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt
git commit -m "feat: execute keyboard actions when voice command is detected"
```

---

## Part B: Speak to Edit

---

### Task B1: Create `EditPrompt` and `EditTextUseCase`

**Files:**
- Create: `app/src/main/java/com/voxpen/app/data/model/EditPrompt.kt`
- Create: `app/src/main/java/com/voxpen/app/domain/usecase/EditTextUseCase.kt`
- Create: `app/src/test/java/com/voxpen/app/data/model/EditPromptTest.kt`
- Create: `app/src/test/java/com/voxpen/app/domain/usecase/EditTextUseCaseTest.kt`

### Step 1: Write failing `EditPrompt` tests

```kotlin
// app/src/test/java/com/voxpen/app/data/model/EditPromptTest.kt
package com.voxpen.app.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EditPromptTest {
    @Test
    fun `should include selected text in prompt`() {
        val prompt = EditPrompt.build(
            selectedText = "hello world",
            instruction = "make it formal",
            language = SttLanguage.English,
        )
        assertThat(prompt).contains("hello world")
        assertThat(prompt).contains("make it formal")
    }

    @Test
    fun `should include Chinese instruction in Chinese prompt`() {
        val prompt = EditPrompt.build(
            selectedText = "你好",
            instruction = "讓它更正式",
            language = SttLanguage.Chinese,
        )
        assertThat(prompt).contains("你好")
        assertThat(prompt).contains("讓它更正式")
        assertThat(prompt).contains("繁體中文")
    }

    @Test
    fun `should instruct LLM to output only revised text`() {
        val prompt = EditPrompt.build("foo", "bar", SttLanguage.English)
        assertThat(prompt.lowercase()).contains("only")
    }
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.EditPromptTest" 2>&1 | tail -10
```
Expected: FAIL

### Step 3: Create `EditPrompt.kt`

```kotlin
// app/src/main/java/com/voxpen/app/data/model/EditPrompt.kt
package com.voxpen.app.data.model

object EditPrompt {
    /**
     * Builds a complete prompt string for the LLM, embedding [selectedText] and [instruction].
     * The LLM receives this as the user message (no system/user split needed).
     */
    fun build(selectedText: String, instruction: String, language: SttLanguage): String =
        when (language) {
            SttLanguage.Chinese, SttLanguage.Auto ->
                "你是文字編輯助手。根據以下選取的文字和編輯指令，只輸出修改後的繁體中文文字。不要加任何說明、解釋或引號。\n\n" +
                    "選取的文字：\n$selectedText\n\n" +
                    "編輯指令：\n$instruction"

            SttLanguage.Japanese ->
                "あなたは文字編集アシスタントです。以下の選択テキストと編集指示に基づき、修正後のテキストのみを出力してください。説明や引用符は不要です。\n\n" +
                    "選択テキスト：\n$selectedText\n\n" +
                    "編集指示：\n$instruction"

            else ->
                "You are a text editing assistant. Given the selected text and editing instruction, output ONLY the revised text. No explanations or quotation marks.\n\n" +
                    "Selected text:\n$selectedText\n\n" +
                    "Editing instruction:\n$instruction"
        }
}
```

### Step 4: Write failing `EditTextUseCase` tests

```kotlin
// app/src/test/java/com/voxpen/app/domain/usecase/EditTextUseCaseTest.kt
package com.voxpen.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatChoice
import com.voxpen.app.data.remote.ChatCompletionApi
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import com.voxpen.app.data.remote.ChatCompletionResponse
import com.voxpen.app.data.remote.ChatMessage
import com.voxpen.app.data.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EditTextUseCaseTest {
    private val chatCompletionApi: ChatCompletionApi = mockk()
    private val apiFactory: ChatCompletionApiFactory = mockk()
    private lateinit var useCase: EditTextUseCase

    @BeforeEach
    fun setUp() {
        every { apiFactory.create(any()) } returns chatCompletionApi
        useCase = EditTextUseCase(LlmRepository(apiFactory))
    }

    @Test
    fun `should return revised text on success`() = runTest {
        coEvery { chatCompletionApi.chatCompletion(any(), any()) } returns
            ChatCompletionResponse(
                choices = listOf(
                    ChatChoice(message = ChatMessage(role = "assistant", content = "A formal Hello World."))
                )
            )

        val result = useCase(
            selectedText = "hello world",
            instruction = "make it formal",
            language = SttLanguage.English,
            apiKey = "test-key",
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("A formal Hello World.")
    }

    @Test
    fun `should fail when api key is blank`() = runTest {
        val result = useCase(
            selectedText = "hello",
            instruction = "make it formal",
            language = SttLanguage.English,
            apiKey = "",
        )
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `should fail when selected text is blank`() = runTest {
        val result = useCase(
            selectedText = "",
            instruction = "make it formal",
            language = SttLanguage.English,
            apiKey = "test-key",
        )
        assertThat(result.isFailure).isTrue()
    }
}
```

### Step 5: Run tests to verify they fail

```bash
./gradlew :app:test --tests "com.voxpen.app.domain.usecase.EditTextUseCaseTest" 2>&1 | tail -10
```
Expected: FAIL

### Step 6: Create `EditTextUseCase.kt`

```kotlin
// app/src/main/java/com/voxpen/app/domain/usecase/EditTextUseCase.kt
package com.voxpen.app.domain.usecase

import com.voxpen.app.data.model.EditPrompt
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.remote.ChatCompletionRequest
import com.voxpen.app.data.remote.ChatCompletionApiFactory
import com.voxpen.app.data.remote.ChatMessage
import com.voxpen.app.data.repository.LlmRepository
import java.io.IOException
import javax.inject.Inject

class EditTextUseCase
    @Inject
    constructor(
        private val llmRepository: LlmRepository,
    ) {
        suspend operator fun invoke(
            selectedText: String,
            instruction: String,
            language: SttLanguage,
            apiKey: String,
            model: String = "llama-3.3-70b-versatile",
            provider: LlmProvider = LlmProvider.Groq,
            customBaseUrl: String? = null,
        ): Result<String> {
            if (apiKey.isBlank()) return Result.failure(IllegalStateException("API key not configured"))
            if (selectedText.isBlank()) return Result.failure(IllegalArgumentException("No text selected"))

            val userMessage = EditPrompt.build(selectedText, instruction, language)
            return llmRepository.editText(userMessage, apiKey, model, provider, customBaseUrl)
        }
    }
```

### Step 7: Add `editText()` to `LlmRepository`

In `LlmRepository.kt`, add a new public function after `refine()`:

```kotlin
/** Sends a fully composed user message to the LLM and returns the response. Used for speak-to-edit. */
suspend fun editText(
    userMessage: String,
    apiKey: String,
    model: String = LLM_MODEL,
    provider: LlmProvider = LlmProvider.Groq,
    customBaseUrl: String? = null,
): Result<String> {
    if (apiKey.isBlank()) return Result.failure(IllegalStateException("API key not configured"))
    if (userMessage.isBlank()) return Result.failure(IllegalArgumentException("Message is empty"))

    return try {
        val api = if (provider == LlmProvider.Custom && !customBaseUrl.isNullOrBlank()) {
            apiFactory.createForCustom(customBaseUrl)
        } else {
            apiFactory.create(provider)
        }
        val request = ChatCompletionRequest(
            model = model,
            messages = listOf(ChatMessage(role = "user", content = userMessage)),
            temperature = TEMPERATURE,
            maxTokens = MAX_TOKENS,
        )
        val response = api.chatCompletion("Bearer $apiKey", request)
        val content = response.choices.firstOrNull()?.message?.content
            ?: return Result.failure(IllegalStateException("No response content"))
        Result.success(content)
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: retrofit2.HttpException) {
        Result.failure(e)
    }
}
```

### Step 8: Run all tests

```bash
./gradlew :app:test --tests "com.voxpen.app.data.model.EditPromptTest" \
                    --tests "com.voxpen.app.domain.usecase.EditTextUseCaseTest" 2>&1 | tail -15
```
Expected: All PASS

### Step 9: Commit

```bash
git add app/src/main/java/com/voxpen/app/data/model/EditPrompt.kt \
        app/src/main/java/com/voxpen/app/domain/usecase/EditTextUseCase.kt \
        app/src/main/java/com/voxpen/app/data/repository/LlmRepository.kt \
        app/src/test/java/com/voxpen/app/data/model/EditPromptTest.kt \
        app/src/test/java/com/voxpen/app/domain/usecase/EditTextUseCaseTest.kt
git commit -m "feat: add EditPrompt, EditTextUseCase, and LlmRepository.editText for speak-to-edit"
```

---

### Task B2: Add Edit Mode to `RecordingController`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt`

### Step 1: Write failing test

In `RecordingControllerTest.kt`, add:

```kotlin
@Test
fun `should emit EditInstruction when editMode is true`() = runTest {
    // Arrange: STT returns the spoken instruction
    every { sttApiFactory.create(any(), any()) } returns groqApi
    coEvery { groqApi.transcribeAudio(any(), any()) } returns WhisperResponse(text = "讓它更正式")

    controller.uiState.test {
        assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
        controller.onStartRecording(startRecording)
        assertThat(awaitItem()).isEqualTo(ImeUiState.Recording)

        controller.onStopRecording(stopRecording, SttLanguage.Chinese, editMode = true)
        assertThat(awaitItem()).isEqualTo(ImeUiState.Processing)

        val editState = awaitItem()
        assertThat(editState).isInstanceOf(ImeUiState.EditInstruction::class.java)
        assertThat((editState as ImeUiState.EditInstruction).instruction).isEqualTo("讓它更正式")
    }
}
```

### Step 2: Run test to verify it fails

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest.should emit EditInstruction when editMode is true" 2>&1 | tail -15
```
Expected: FAIL

### Step 3: Add `editMode` param to `RecordingController.onStopRecording()`

Change the function signature to:

```kotlin
fun onStopRecording(
    stopRecording: () -> ByteArray,
    language: SttLanguage,
    editMode: Boolean = false,    // NEW
)
```

Inside the `onSuccess` block, after `usageLimiter.incrementVoiceInput()`, add the edit mode early return **before** the voice command check:

```kotlin
onSuccess = { originalText ->
    if (!proStatus.isPro) {
        usageLimiter.incrementVoiceInput()
    }

    // NEW: edit mode — emit instruction for VoxPenIME to handle
    if (editMode) {
        _uiState.value = ImeUiState.EditInstruction(originalText)
        return@launch
    }

    // Voice command check
    val command = VoiceCommandRecognizer.recognize(originalText)
    if (command != null) {
        _uiState.value = ImeUiState.CommandDetected(command)
        return@launch
    }

    // ... existing refinement flow ...
}
```

### Step 4: Run tests to verify they pass

```bash
./gradlew :app:test --tests "com.voxpen.app.ime.RecordingControllerTest" 2>&1 | tail -15
```
Expected: All PASS

### Step 5: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/RecordingController.kt \
        app/src/test/java/com/voxpen/app/ime/RecordingControllerTest.kt
git commit -m "feat: add editMode param to RecordingController for speak-to-edit flow"
```

---

### Task B3: Expose `EditTextUseCase` via `VoxPenIMEEntryPoint`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIMEEntryPoint.kt`

### Step 1: Read the current entry point

Open `VoxPenIMEEntryPoint.kt` and add `EditTextUseCase` alongside the existing use cases:

```kotlin
fun editTextUseCase(): EditTextUseCase
```

Add the import:
```kotlin
import com.voxpen.app.domain.usecase.EditTextUseCase
```

### Step 2: Build to verify

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL (Hilt auto-provides `EditTextUseCase` since it has `@Inject` constructor)

### Step 3: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIMEEntryPoint.kt
git commit -m "feat: expose EditTextUseCase via VoxPenIMEEntryPoint"
```

---

### Task B4: Wire Speak-to-Edit into `VoxPenIME`

**Files:**
- Modify: `app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

This is IME code — no unit tests. Verify manually.

### Step 1: Add strings

In `app/src/main/res/values/strings.xml`:
```xml
<string name="edit_mode">✏️ Edit Mode</string>
<string name="edit_mode_active">✏️ Edit: select text then tap mic</string>
<string name="editing_text">Editing…</string>
```

In `app/src/main/res/values-zh-rTW/strings.xml`:
```xml
<string name="edit_mode">✏️ 編輯模式</string>
<string name="edit_mode_active">✏️ 編輯中：選取文字後點麥克風</string>
<string name="editing_text">編輯中…</string>
```

### Step 2: Add `isEditMode` field and `editTextUseCase` to `VoxPenIME`

In `VoxPenIME.kt`, add at the top of the class body (alongside other `private var` fields):

```kotlin
private var isEditMode: Boolean = false
private lateinit var editTextUseCase: EditTextUseCase
```

In `onCreateInputView()`, after the line that sets `preferencesManager = ...`, add:
```kotlin
editTextUseCase = entryPoint.editTextUseCase()
```

Add import:
```kotlin
import com.voxpen.app.domain.usecase.EditTextUseCase
```

### Step 3: Add "Edit Mode" to quick settings popup

In `showQuickSettings()`, after `addRefinementToggle(...)`, add:

```kotlin
addEditModeToggle(container, popup, dp)
```

Implement the helper:

```kotlin
private fun addEditModeToggle(container: LinearLayout, popup: PopupWindow, dp: Float) {
    val label = if (isEditMode) {
        "${getString(R.string.edit_mode)} ✓"
    } else {
        getString(R.string.edit_mode)
    }
    val tv = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(resources.getColor(R.color.key_text, null))
        val pad = (8 * dp).toInt()
        setPadding(pad, pad, pad, pad)
        setOnClickListener {
            isEditMode = !isEditMode
            updateEditModeIndicator()
            popup.dismiss()
        }
    }
    container.addView(tv)
}

private fun updateEditModeIndicator() {
    if (isEditMode) {
        candidateBar?.visibility = View.VISIBLE
        showStatusRow(getString(R.string.edit_mode_active), showProgress = false)
    } else {
        if (recordingController.uiState.value == ImeUiState.Idle) {
            candidateBar?.visibility = View.GONE
        }
    }
}
```

### Step 4: Pass `editMode` to `onStopRecording()`

In `stopRecording()`, change the call to:

```kotlin
recordingController.onStopRecording(
    stopRecording = { audioRecorder.stopRecording() },
    language = language,
    editMode = isEditMode,  // NEW
)
```

### Step 5: Handle `EditInstruction` and `EditResult` in `updateCandidateBar()`

In `updateCandidateBar()`, add the new cases:

```kotlin
is ImeUiState.EditInstruction -> {
    timerHandler.removeCallbacks(timerRunnable)
    showStatusRow(getString(R.string.editing_text), showProgress = true)
    performEditWithLlm(state.instruction)
}

is ImeUiState.Editing -> {
    // already showing spinner from EditInstruction handler
}

is ImeUiState.EditResult -> {
    timerHandler.removeCallbacks(timerRunnable)
    // Commit revised text, replacing selection
    currentInputConnection?.commitText(state.revised, 1)
    isEditMode = false
    recordingController.dismiss()
}
```

### Step 6: Implement `performEditWithLlm()`

Add this private function to `VoxPenIME`:

```kotlin
private fun performEditWithLlm(instruction: String) {
    val selectedText = currentInputConnection?.getSelectedText(0)?.toString()
    if (selectedText.isNullOrBlank()) {
        showStatusRow("⚠️ No text selected", showProgress = false)
        candidateBar?.postDelayed({ recordingController.dismiss() }, 2000)
        return
    }

    serviceScope.launch {
        val apiKey = preferencesManager.llmProviderFlow.first().let { provider ->
            // Reuse existing key resolution pattern from RecordingController
            preferencesManager.languageFlow.first() // just to get the scope going
            // Actually, read from ApiKeyManager — but VoxPenIME doesn't have direct access.
            // Use the same approach: read from entryPoint
            entryPoint.apiKeyManager().getApiKey(provider)
                ?: entryPoint.apiKeyManager().getGroqApiKey()
        } ?: run {
            showStatusRow("API key not configured", showProgress = false)
            return@launch
        }

        val language = preferencesManager.languageFlow.first()
        val llmProvider = preferencesManager.llmProviderFlow.first()
        val llmModel = preferencesManager.llmModelFlow.first()

        val result = editTextUseCase(
            selectedText = selectedText,
            instruction = instruction,
            language = language,
            apiKey = apiKey,
            model = llmModel,
            provider = llmProvider,
        )

        result.fold(
            onSuccess = { revised ->
                // Emit EditResult — updateCandidateBar will commit text
                // Use a direct approach since we're already in VoxPenIME
                currentInputConnection?.commitText(revised, 1)
                isEditMode = false
                recordingController.dismiss()
            },
            onFailure = { err ->
                showStatusRow("Edit failed: ${err.message}", showProgress = false)
                candidateBar?.postDelayed({ recordingController.dismiss() }, 2000)
            },
        )
    }
}
```

> **Important:** The `performEditWithLlm` function accesses `ApiKeyManager` via the entry point. In `onCreateInputView()`, store the entryPoint reference: add `private lateinit var entryPoint: VoxPenIMEEntryPoint` and assign it in `onCreateInputView()`.

### Step 7: Build to verify

```bash
./gradlew :app:assembleDebug 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL. Fix any compile errors before proceeding.

### Step 8: Run full test suite

```bash
./gradlew :app:test 2>&1 | tail -10
```
Expected: All PASS

### Step 9: Manual test — Speak to Edit

1. Open any app with a text field (e.g., Notes, Messages)
2. Type: "我們明天可以喝咖啡嗎" (informal)
3. Select that text
4. Activate VoxPen keyboard → long-press ⚙️ → tap "✏️ Edit Mode"
5. Verify candidate bar shows "✏️ Edit: select text then tap mic"
6. Tap mic → speak "讓它更正式" → verify the selection is replaced with a formal version
7. Tap ⚙️ long-press → tap "✏️ Edit Mode ✓" to exit edit mode
8. Verify keyboard returns to normal dictation mode

### Step 10: Commit

```bash
git add app/src/main/java/com/voxpen/app/ime/VoxPenIME.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: implement speak-to-edit in VoxPenIME with edit mode toggle"
```

---

## Final Verification

```bash
./gradlew :app:test 2>&1 | tail -10
./gradlew :app:assembleDebug 2>&1 | tail -5
```

Both must succeed. Then run the full manual test sequence:
1. Voice commands: "送出", "刪除", "換行", "send", "delete"
2. Speak to Edit: select text → edit mode → speak instruction → verify replacement
