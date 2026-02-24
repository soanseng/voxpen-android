# Custom Vocabulary Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a user-managed vocabulary list that biases Whisper STT and LLM refinement to improve recognition of proper nouns, names, and homophones.

**Architecture:** New Room entity + DAO + repository for vocabulary storage. Vocabulary injected into Whisper `prompt` parameter (truncated to ~200 tokens) and LLM system prompt suffix (full list). Dedicated Compose screen for vocabulary management, navigated from Settings. Free: 10 entries max, Pro: unlimited.

**Tech Stack:** Room (entity/DAO/migration), Hilt (DI), Jetpack Compose (UI), Turbine + MockK + Truth (tests), Kotlin Coroutines + Flow

**Design doc:** `docs/plans/2026-02-24-custom-vocabulary-design.md`

---

### Task 1: Room Entity — DictionaryEntry

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/local/DictionaryEntry.kt`
- Test: `app/src/test/java/com/voxink/app/data/local/DictionaryEntryTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.voxink.app.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DictionaryEntryTest {
    @Test
    fun `should have default id of zero`() {
        val entry = DictionaryEntry(word = "語墨", createdAt = 1000L)
        assertThat(entry.id).isEqualTo(0)
    }

    @Test
    fun `should store word and createdAt`() {
        val entry = DictionaryEntry(word = "Anthropic", createdAt = 1234567890L)
        assertThat(entry.word).isEqualTo("Anthropic")
        assertThat(entry.createdAt).isEqualTo(1234567890L)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.local.DictionaryEntryTest" --no-build-cache 2>&1 | tail -20`
Expected: FAIL — `DictionaryEntry` class not found

**Step 3: Write minimal implementation**

```kotlin
package com.voxink.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_entries",
    indices = [Index(value = ["word"], unique = true)],
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val createdAt: Long,
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.local.DictionaryEntryTest" --no-build-cache 2>&1 | tail -20`
Expected: PASS (2 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/local/DictionaryEntry.kt app/src/test/java/com/voxink/app/data/local/DictionaryEntryTest.kt
git commit -m "feat: add DictionaryEntry Room entity"
```

---

### Task 2: Room DAO — DictionaryDao

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/local/DictionaryDao.kt`

**Step 1: Write the DAO interface**

```kotlin
package com.voxink.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DictionaryEntry>>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DictionaryEntry): Long

    @Delete
    suspend fun delete(entry: DictionaryEntry)

    @Query("SELECT word FROM dictionary_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getWords(limit: Int = 500): List<String>
}
```

Note: DAO is an interface — Room generates the implementation at compile time. Unit testing DAOs requires an in-memory Room database which is an Android instrumented test. We will verify correctness through the repository tests (Task 4) and integration at build time.

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (DAO compiles but is not yet wired to AppDatabase — that's Task 3)

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/local/DictionaryDao.kt
git commit -m "feat: add DictionaryDao with CRUD and vocabulary query"
```

---

### Task 3: Wire AppDatabase + Migration + DI

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/voxink/app/di/AppModule.kt`

**Step 1: Update AppDatabase — add entity, version bump, migration**

In `AppDatabase.kt`, change to:

```kotlin
package com.voxink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TranscriptionEntity::class, DictionaryEntry::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS dictionary_entries (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            word TEXT NOT NULL,
                            createdAt INTEGER NOT NULL
                        )""",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_dictionary_entries_word ON dictionary_entries (word)",
                    )
                }
            }
    }
}
```

**Step 2: Update AppModule — add migration + provide DictionaryDao**

In `AppModule.kt`, modify `provideAppDatabase` and add `provideDictionaryDao`:

```kotlin
@Provides
@Singleton
fun provideAppDatabase(
    @ApplicationContext context: Context,
): AppDatabase =
    Room
        .databaseBuilder(context, AppDatabase::class.java, "voxink.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

@Provides
fun provideDictionaryDao(database: AppDatabase): DictionaryDao = database.dictionaryDao()
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/local/AppDatabase.kt app/src/main/java/com/voxink/app/di/AppModule.kt
git commit -m "feat: wire DictionaryDao to AppDatabase with migration v1→v2"
```

---

### Task 4: DictionaryRepository

**Files:**
- Create: `app/src/main/java/com/voxink/app/data/repository/DictionaryRepository.kt`
- Create: `app/src/test/java/com/voxink/app/data/repository/DictionaryRepositoryTest.kt`

**Step 1: Write the failing tests**

```kotlin
package com.voxink.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.local.DictionaryDao
import com.voxink.app.data.local.DictionaryEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DictionaryRepositoryTest {
    private val dao: DictionaryDao = mockk()
    private lateinit var repository: DictionaryRepository

    @BeforeEach
    fun setUp() {
        repository = DictionaryRepository(dao)
    }

    @Test
    fun `should return all entries from dao`() =
        runTest {
            val entries = listOf(
                DictionaryEntry(id = 1, word = "語墨", createdAt = 2000L),
                DictionaryEntry(id = 2, word = "Anthropic", createdAt = 1000L),
            )
            every { dao.getAll() } returns flowOf(entries)

            val result = repository.getAll().first()

            assertThat(result).hasSize(2)
            assertThat(result[0].word).isEqualTo("語墨")
        }

    @Test
    fun `should return count from dao`() =
        runTest {
            every { dao.count() } returns flowOf(5)

            val result = repository.count().first()

            assertThat(result).isEqualTo(5)
        }

    @Test
    fun `should trim word before inserting`() =
        runTest {
            val slot = slot<DictionaryEntry>()
            coEvery { dao.insert(capture(slot)) } returns 1L

            repository.add("  語墨  ")

            assertThat(slot.captured.word).isEqualTo("語墨")
            assertThat(slot.captured.createdAt).isGreaterThan(0L)
        }

    @Test
    fun `should delegate delete to dao`() =
        runTest {
            val entry = DictionaryEntry(id = 1, word = "test", createdAt = 1000L)
            coEvery { dao.delete(entry) } returns Unit

            repository.remove(entry)

            coVerify { dao.delete(entry) }
        }

    @Test
    fun `should return words with limit from dao`() =
        runTest {
            coEvery { dao.getWords(80) } returns listOf("語墨", "Claude")

            val result = repository.getWords(80)

            assertThat(result).containsExactly("語墨", "Claude")
        }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.repository.DictionaryRepositoryTest" --no-build-cache 2>&1 | tail -20`
Expected: FAIL — `DictionaryRepository` class not found

**Step 3: Write minimal implementation**

```kotlin
package com.voxink.app.data.repository

import com.voxink.app.data.local.DictionaryDao
import com.voxink.app.data.local.DictionaryEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository
    @Inject
    constructor(
        private val dao: DictionaryDao,
    ) {
        fun getAll(): Flow<List<DictionaryEntry>> = dao.getAll()

        fun count(): Flow<Int> = dao.count()

        suspend fun add(word: String): Long =
            dao.insert(
                DictionaryEntry(
                    word = word.trim(),
                    createdAt = System.currentTimeMillis(),
                ),
            )

        suspend fun remove(entry: DictionaryEntry) = dao.delete(entry)

        suspend fun getWords(limit: Int = 500): List<String> = dao.getWords(limit)
    }
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.repository.DictionaryRepositoryTest" --no-build-cache 2>&1 | tail -20`
Expected: PASS (5 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/repository/DictionaryRepository.kt app/src/test/java/com/voxink/app/data/repository/DictionaryRepositoryTest.kt
git commit -m "feat: add DictionaryRepository with CRUD operations"
```

---

### Task 5: Vocabulary Prompt Builder Utility

**Files:**
- Create: `app/src/main/java/com/voxink/app/util/VocabularyPromptBuilder.kt`
- Create: `app/src/test/java/com/voxink/app/util/VocabularyPromptBuilderTest.kt`

This utility builds the Whisper prompt hint and the LLM prompt suffix from a word list, handling token budget truncation for Whisper.

**Step 1: Write the failing tests**

```kotlin
package com.voxink.app.util

import com.google.common.truth.Truth.assertThat
import com.voxink.app.data.model.SttLanguage
import org.junit.jupiter.api.Test

class VocabularyPromptBuilderTest {
    @Test
    fun `should return base prompt when vocabulary is empty`() {
        val result = VocabularyPromptBuilder.buildWhisperPrompt(SttLanguage.Chinese, emptyList())
        assertThat(result).isEqualTo("繁體中文轉錄。")
    }

    @Test
    fun `should append vocabulary to base prompt`() {
        val result = VocabularyPromptBuilder.buildWhisperPrompt(
            SttLanguage.Chinese,
            listOf("語墨", "Anthropic"),
        )
        assertThat(result).isEqualTo("繁體中文轉錄。語墨, Anthropic")
    }

    @Test
    fun `should truncate vocabulary when exceeding token budget`() {
        // Each CJK char ≈ 2 tokens. Budget is ~200 tokens = ~100 CJK chars.
        // Create words that will exceed budget.
        val longWords = (1..120).map { "詞彙$it" }
        val result = VocabularyPromptBuilder.buildWhisperPrompt(SttLanguage.Chinese, longWords)
        // Should be shorter than including all 120 words
        assertThat(result.length).isLessThan(
            "繁體中文轉錄。".length + longWords.joinToString(", ").length,
        )
        // Should still start with base prompt
        assertThat(result).startsWith("繁體中文轉錄。")
    }

    @Test
    fun `should return empty string for LLM suffix when vocabulary is empty`() {
        val result = VocabularyPromptBuilder.buildLlmSuffix(SttLanguage.Chinese, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `should build Chinese LLM suffix`() {
        val result = VocabularyPromptBuilder.buildLlmSuffix(
            SttLanguage.Chinese,
            listOf("語墨", "Anthropic"),
        )
        assertThat(result).contains("術語表")
        assertThat(result).contains("語墨")
        assertThat(result).contains("Anthropic")
    }

    @Test
    fun `should build English LLM suffix`() {
        val result = VocabularyPromptBuilder.buildLlmSuffix(
            SttLanguage.English,
            listOf("VoxInk", "Claude"),
        )
        assertThat(result).contains("Vocabulary")
        assertThat(result).contains("VoxInk")
        assertThat(result).contains("Claude")
    }

    @Test
    fun `should build Japanese LLM suffix`() {
        val result = VocabularyPromptBuilder.buildLlmSuffix(
            SttLanguage.Japanese,
            listOf("語墨"),
        )
        assertThat(result).contains("用語集")
        assertThat(result).contains("語墨")
    }

    @Test
    fun `should estimate CJK tokens as roughly 2 per char`() {
        val tokens = VocabularyPromptBuilder.estimateTokens("語墨")
        assertThat(tokens).isEqualTo(4) // 2 chars * 2
    }

    @Test
    fun `should estimate Latin tokens as roughly 0.25 per char`() {
        val tokens = VocabularyPromptBuilder.estimateTokens("Anthropic")
        // 9 chars / 4 = 2.25, ceil = 3
        assertThat(tokens).isEqualTo(3)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.util.VocabularyPromptBuilderTest" --no-build-cache 2>&1 | tail -20`
Expected: FAIL — `VocabularyPromptBuilder` not found

**Step 3: Write minimal implementation**

```kotlin
package com.voxink.app.util

import com.voxink.app.data.model.SttLanguage
import kotlin.math.ceil

object VocabularyPromptBuilder {
    private const val WHISPER_TOKEN_BUDGET = 200

    fun buildWhisperPrompt(
        language: SttLanguage,
        vocabulary: List<String>,
    ): String {
        val basePrompt = language.prompt
        if (vocabulary.isEmpty()) return basePrompt

        val baseTokens = estimateTokens(basePrompt)
        val remainingBudget = WHISPER_TOKEN_BUDGET - baseTokens
        if (remainingBudget <= 0) return basePrompt

        val selected = mutableListOf<String>()
        var usedTokens = 0
        for (word in vocabulary) {
            val wordTokens = estimateTokens(word) + 1 // +1 for ", " separator
            if (usedTokens + wordTokens > remainingBudget) break
            selected.add(word)
            usedTokens += wordTokens
        }

        if (selected.isEmpty()) return basePrompt
        return basePrompt + selected.joinToString(", ")
    }

    fun buildLlmSuffix(
        language: SttLanguage,
        vocabulary: List<String>,
    ): String {
        if (vocabulary.isEmpty()) return ""

        val words = vocabulary.joinToString(", ")
        return when (language) {
            SttLanguage.English ->
                "\nVocabulary (prefer these terms): $words"
            SttLanguage.Japanese ->
                "\n用語集（これらの用語を優先してください）：$words"
            else ->
                "\n術語表（請優先使用這些詞彙）：$words"
        }
    }

    fun estimateTokens(text: String): Int {
        var cjkChars = 0
        var latinChars = 0
        for (ch in text) {
            if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF || ch.code in 0x3040..0x309F || ch.code in 0x30A0..0x30FF) {
                cjkChars++
            } else {
                latinChars++
            }
        }
        return cjkChars * 2 + ceil(latinChars / 4.0).toInt()
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.util.VocabularyPromptBuilderTest" --no-build-cache 2>&1 | tail -20`
Expected: PASS (9 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/util/VocabularyPromptBuilder.kt app/src/test/java/com/voxink/app/util/VocabularyPromptBuilderTest.kt
git commit -m "feat: add VocabularyPromptBuilder for Whisper and LLM prompt injection"
```

---

### Task 6: Thread Vocabulary Through STT Pipeline

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/repository/SttRepository.kt`
- Modify: `app/src/main/java/com/voxink/app/domain/usecase/TranscribeAudioUseCase.kt`
- Modify: `app/src/test/java/com/voxink/app/data/repository/SttRepositoryTest.kt`
- Modify: `app/src/test/java/com/voxink/app/domain/usecase/TranscribeAudioUseCaseTest.kt`

**Step 1: Write the failing test in SttRepositoryTest**

Add this test to the existing `SttRepositoryTest.kt`:

```kotlin
@Test
fun `should use custom prompt when vocabularyHint is provided`() =
    runTest {
        // ... (mock groqApi.transcribe to return success)
        val result = repository.transcribe(
            wavBytes = byteArrayOf(1, 2, 3),
            language = SttLanguage.Chinese,
            apiKey = "key",
            vocabularyHint = "繁體中文轉錄。語墨, Claude",
        )
        // Verify the prompt sent to API contains the vocabulary hint
        // (verification depends on existing test pattern — use coVerify or MockWebServer request capture)
    }
```

**Step 2: Run test to verify it fails**

Expected: FAIL — `vocabularyHint` parameter doesn't exist

**Step 3: Modify SttRepository.transcribe()**

Add `vocabularyHint: String? = null` parameter. If provided, use it as the prompt instead of `language.prompt`:

In `SttRepository.kt` line 18, change the signature:

```kotlin
suspend fun transcribe(
    wavBytes: ByteArray,
    language: SttLanguage,
    apiKey: String,
    model: String = WHISPER_MODEL,
    vocabularyHint: String? = null,
): Result<String> {
```

At line 38, change prompt construction:

```kotlin
val promptBody = (vocabularyHint ?: language.prompt).toRequestBody(TEXT_PLAIN)
```

**Step 4: Modify TranscribeAudioUseCase.invoke()**

Add `vocabularyHint: String? = null` parameter and pass it through:

```kotlin
suspend operator fun invoke(
    pcmData: ByteArray,
    language: SttLanguage,
    apiKey: String,
    model: String = "whisper-large-v3-turbo",
    vocabularyHint: String? = null,
    sampleRate: Int = SAMPLE_RATE,
    channels: Int = CHANNELS,
    bitsPerSample: Int = BITS_PER_SAMPLE,
): Result<String> {
    val wavBytes = AudioEncoder.pcmToWav(pcmData, sampleRate, channels, bitsPerSample)
    return sttRepository.transcribe(wavBytes, language, apiKey, model, vocabularyHint)
}
```

**Step 5: Update existing tests — ensure they still pass with default null**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.repository.SttRepositoryTest" --tests "com.voxink.app.domain.usecase.TranscribeAudioUseCaseTest" --no-build-cache 2>&1 | tail -20`
Expected: PASS — all existing tests pass (new param has default value)

**Step 6: Run the new test**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.repository.SttRepositoryTest" --no-build-cache 2>&1 | tail -20`
Expected: PASS (all tests including new one)

**Step 7: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/repository/SttRepository.kt app/src/main/java/com/voxink/app/domain/usecase/TranscribeAudioUseCase.kt app/src/test/java/com/voxink/app/data/repository/SttRepositoryTest.kt app/src/test/java/com/voxink/app/domain/usecase/TranscribeAudioUseCaseTest.kt
git commit -m "feat: thread vocabularyHint through STT pipeline"
```

---

### Task 7: Thread Vocabulary Through LLM Pipeline

**Files:**
- Modify: `app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt`
- Modify: `app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt`
- Modify: `app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt`
- Modify: `app/src/test/java/com/voxink/app/data/model/RefinementPromptTest.kt`
- Modify: `app/src/test/java/com/voxink/app/data/repository/LlmRepositoryTest.kt`

**Step 1: Update RefinementPrompt to accept vocabulary**

Add a new function in `RefinementPrompt.kt`:

```kotlin
fun forLanguage(language: SttLanguage, vocabulary: List<String> = emptyList()): String {
    val base = when (language) {
        SttLanguage.Chinese -> PROMPT_ZH
        SttLanguage.English -> PROMPT_EN
        SttLanguage.Japanese -> PROMPT_JA
        SttLanguage.Auto -> PROMPT_MIXED
    }
    val suffix = VocabularyPromptBuilder.buildLlmSuffix(language, vocabulary)
    return base + suffix
}
```

Add import: `import com.voxink.app.util.VocabularyPromptBuilder`

**Step 2: Write failing test in RefinementPromptTest**

Add to existing `RefinementPromptTest.kt`:

```kotlin
@Test
fun `should append vocabulary suffix when provided`() {
    val prompt = RefinementPrompt.forLanguage(
        SttLanguage.Chinese,
        listOf("語墨", "Claude"),
    )
    assertThat(prompt).contains("術語表")
    assertThat(prompt).contains("語墨")
    assertThat(prompt).contains("Claude")
}

@Test
fun `should not append suffix when vocabulary is empty`() {
    val withVocab = RefinementPrompt.forLanguage(SttLanguage.Chinese, emptyList())
    val without = RefinementPrompt.forLanguage(SttLanguage.Chinese)
    assertThat(withVocab).isEqualTo(without)
}
```

**Step 3: Update LlmRepository.refine()**

Add `vocabulary: List<String> = emptyList()` parameter:

```kotlin
suspend fun refine(
    text: String,
    language: SttLanguage,
    apiKey: String,
    model: String = LLM_MODEL,
    vocabulary: List<String> = emptyList(),
): Result<String> {
```

Change line 32 to:

```kotlin
val systemPrompt = RefinementPrompt.forLanguage(language, vocabulary)
```

**Step 4: Update RefineTextUseCase.invoke()**

Add `vocabulary: List<String> = emptyList()` parameter and pass through:

```kotlin
suspend operator fun invoke(
    text: String,
    language: SttLanguage,
    apiKey: String,
    model: String = "llama-3.3-70b-versatile",
    vocabulary: List<String> = emptyList(),
): Result<String> = llmRepository.refine(text, language, apiKey, model, vocabulary)
```

**Step 5: Write failing test in LlmRepositoryTest**

Add to existing `LlmRepositoryTest.kt`:

```kotlin
@Test
fun `should include vocabulary in system prompt when provided`() =
    runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"c5","choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""",
                )
                .setHeader("Content-Type", "application/json"),
        )

        repository.refine("text", SttLanguage.Chinese, "key", vocabulary = listOf("語墨", "Claude"))

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("術語表")
        assertThat(body).contains("語墨")
    }
```

**Step 6: Run all affected tests**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.data.model.RefinementPromptTest" --tests "com.voxink.app.data.repository.LlmRepositoryTest" --tests "com.voxink.app.domain.usecase.RefineTextUseCaseTest" --no-build-cache 2>&1 | tail -30`
Expected: PASS — all tests including new ones

**Step 7: Commit**

```bash
git add app/src/main/java/com/voxink/app/data/model/RefinementPrompt.kt app/src/main/java/com/voxink/app/data/repository/LlmRepository.kt app/src/main/java/com/voxink/app/domain/usecase/RefineTextUseCase.kt app/src/test/java/com/voxink/app/data/model/RefinementPromptTest.kt app/src/test/java/com/voxink/app/data/repository/LlmRepositoryTest.kt
git commit -m "feat: thread vocabulary through LLM refinement pipeline"
```

---

### Task 8: Wire Vocabulary into RecordingController

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/RecordingController.kt`
- Modify: `app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt`

**Step 1: Write the failing test**

Add to `RecordingControllerTest.kt`. First, add a mock `DictionaryRepository` to the test setup:

```kotlin
private val dictionaryRepository: DictionaryRepository = mockk()
```

In `setUp()`, add:

```kotlin
coEvery { dictionaryRepository.getWords(any()) } returns listOf("語墨", "Claude")
```

And pass it to the constructor:

```kotlin
controller = RecordingController(
    transcribeUseCase = transcribeUseCase,
    refineTextUseCase = refineTextUseCase,
    apiKeyManager = apiKeyManager,
    preferencesManager = preferencesManager,
    dictionaryRepository = dictionaryRepository,
    usageLimiter = usageLimiter,
    proStatusProvider = { proStatus },
    ioDispatcher = testDispatcher,
)
```

Add test:

```kotlin
@Test
fun `should fetch vocabulary and pass to transcription and refinement`() =
    runTest {
        coEvery {
            groqApi.transcribe(any(), any(), any(), any(), any(), any())
        } returns WhisperResponse(text = "語末你好")
        coEvery {
            groqApi.chatCompletion(any(), any())
        } returns chatResponse("語墨你好")

        controller.uiState.test {
            assertThat(awaitItem()).isEqualTo(ImeUiState.Idle)
            controller.onStartRecording(startRecording)
            skipItems(1)
            controller.onStopRecording(stopRecording, SttLanguage.Chinese)
            // Drain states until Refined
            val states = mutableListOf(awaitItem())
            states.add(awaitItem())
            val finalState = states.last()
            assertThat(finalState).isEqualTo(
                ImeUiState.Refined("語末你好", "語墨你好"),
            )
        }

        // Verify vocabulary was fetched
        coVerify { dictionaryRepository.getWords(any()) }
    }
```

**Step 2: Run test to verify it fails**

Expected: FAIL — `dictionaryRepository` parameter doesn't exist in `RecordingController`

**Step 3: Modify RecordingController**

Add `DictionaryRepository` to constructor parameters:

```kotlin
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
```

Add import: `import com.voxink.app.data.repository.DictionaryRepository`
Add import: `import com.voxink.app.util.VocabularyPromptBuilder`

In `onStopRecording()`, after line 68 (`_uiState.value = ImeUiState.Processing`) inside the `scope.launch`:

```kotlin
scope.launch {
    val proStatus = proStatusProvider()
    val vocabulary = dictionaryRepository.getWords(80)
    val whisperPrompt = VocabularyPromptBuilder.buildWhisperPrompt(language, vocabulary)
    val result = transcribeUseCase(pcmData, language, apiKey, sttModel, vocabularyHint = whisperPrompt)
    result.fold(
        onSuccess = { originalText ->
            if (!proStatus.isPro) {
                usageLimiter.incrementVoiceInput()
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
            val refinedResult = refineTextUseCase(originalText, language, apiKey, llmModel, allVocabulary)
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
```

**Step 4: Update all existing RecordingControllerTest setUp to include dictionaryRepository mock**

Every test in this file uses `controller` which now needs the new parameter. The `setUp()` already configures it (Step 1). All existing tests should pass with the default mock returning `listOf("語墨", "Claude")`.

**Step 5: Run all RecordingController tests**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.ime.RecordingControllerTest" --no-build-cache 2>&1 | tail -30`
Expected: PASS — all existing + new test

**Step 6: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/RecordingController.kt app/src/test/java/com/voxink/app/ime/RecordingControllerTest.kt
git commit -m "feat: wire vocabulary into RecordingController for STT and LLM"
```

---

### Task 9: Update VoxInkIME to Provide DictionaryRepository

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ime/VoxInkIME.kt`

`VoxInkIME` creates `RecordingController` manually (not via Hilt — IME services use `EntryPointAccessors`). It needs to get `DictionaryRepository` from Hilt and pass it to the controller.

**Step 1: Read VoxInkIME.kt to understand current Hilt entry point pattern**

The file uses `EntryPointAccessors.fromApplication()` to get dependencies. Look for the `@EntryPoint` interface inside or near the class.

**Step 2: Add DictionaryRepository to the entry point interface**

Add to the `@EntryPoint` interface:

```kotlin
fun dictionaryRepository(): DictionaryRepository
```

**Step 3: Pass it to RecordingController constructor**

Where `RecordingController(...)` is instantiated, add `dictionaryRepository = entryPoint.dictionaryRepository()`.

**Step 4: Verify build compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/ime/VoxInkIME.kt
git commit -m "feat: provide DictionaryRepository to VoxInkIME"
```

---

### Task 10: i18n Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Step 1: Add English strings**

Add to `values/strings.xml` (before the closing `</resources>` tag, or in a logical section):

```xml
<!-- Dictionary / Custom Vocabulary -->
<string name="dictionary_title">Custom Vocabulary</string>
<string name="dictionary_add_hint">Add a word…</string>
<string name="dictionary_add_button">Add</string>
<string name="dictionary_count">%1$d / %2$d words</string>
<string name="dictionary_count_unlimited">%1$d words</string>
<string name="dictionary_empty">Add names, places, and terms to improve voice recognition accuracy</string>
<string name="dictionary_duplicate">Word already exists</string>
<string name="dictionary_upgrade">Upgrade to Pro for unlimited vocabulary</string>
<string name="dictionary_help">Words you add here help the voice engine recognize names, places, and special terms more accurately.</string>
```

**Step 2: Add Traditional Chinese strings**

Add to `values-zh-rTW/strings.xml`:

```xml
<!-- Dictionary / Custom Vocabulary -->
<string name="dictionary_title">自定義詞彙</string>
<string name="dictionary_add_hint">輸入新詞彙…</string>
<string name="dictionary_add_button">新增</string>
<string name="dictionary_count">%1$d / %2$d 個詞彙</string>
<string name="dictionary_count_unlimited">%1$d 個詞彙</string>
<string name="dictionary_empty">加入人名、地名、專有名詞，提升語音辨識準確度</string>
<string name="dictionary_duplicate">此詞彙已存在</string>
<string name="dictionary_upgrade">升級 Pro 版解鎖無限詞彙</string>
<string name="dictionary_help">加入的詞彙會協助語音引擎更準確地辨識人名、地名與專有名詞。</string>
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "feat: add i18n strings for custom vocabulary screen"
```

---

### Task 11: DictionaryViewModel

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/dictionary/DictionaryViewModel.kt`
- Create: `app/src/test/java/com/voxink/app/ui/dictionary/DictionaryViewModelTest.kt`

**Step 1: Write the failing tests**

```kotlin
package com.voxink.app.ui.dictionary

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxink.app.billing.BillingManager
import com.voxink.app.billing.ProStatus
import com.voxink.app.data.local.DictionaryEntry
import com.voxink.app.data.repository.DictionaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {
    private val repository: DictionaryRepository = mockk()
    private val billingManager: BillingManager = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val entriesFlow = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    private val countFlow = MutableStateFlow(0)
    private val proStatusFlow = MutableStateFlow<ProStatus>(ProStatus.Free)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAll() } returns entriesFlow
        every { repository.count() } returns countFlow
        every { billingManager.proStatus } returns proStatusFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DictionaryViewModel(repository, billingManager)

    @Test
    fun `should expose entries from repository`() =
        runTest {
            val vm = createViewModel()
            val entry = DictionaryEntry(id = 1, word = "語墨", createdAt = 1000L)
            entriesFlow.value = listOf(entry)

            vm.entries.test {
                assertThat(awaitItem()).containsExactly(entry)
            }
        }

    @Test
    fun `should expose count from repository`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 3

            vm.count.test {
                assertThat(awaitItem()).isEqualTo(3)
            }
        }

    @Test
    fun `should expose isPro from billing manager`() =
        runTest {
            val vm = createViewModel()
            proStatusFlow.value = ProStatus.Pro

            vm.isPro.test {
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `should add word via repository`() =
        runTest {
            coEvery { repository.add(any()) } returns 1L
            val vm = createViewModel()

            vm.addWord("語墨")

            coVerify { repository.add("語墨") }
        }

    @Test
    fun `should not add blank word`() =
        runTest {
            val vm = createViewModel()

            vm.addWord("   ")

            coVerify(exactly = 0) { repository.add(any()) }
        }

    @Test
    fun `should detect duplicate when insert returns -1`() =
        runTest {
            coEvery { repository.add(any()) } returns -1L
            val vm = createViewModel()

            vm.addWord("語墨")

            vm.showDuplicateToast.test {
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `should remove entry via repository`() =
        runTest {
            val entry = DictionaryEntry(id = 1, word = "test", createdAt = 1000L)
            coEvery { repository.remove(entry) } returns Unit
            val vm = createViewModel()

            vm.removeWord(entry)

            coVerify { repository.remove(entry) }
        }

    @Test
    fun `should report limit reached for Free user at 10 entries`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 10
            proStatusFlow.value = ProStatus.Free

            vm.isLimitReached.test {
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `should not report limit reached for Pro user`() =
        runTest {
            val vm = createViewModel()
            countFlow.value = 100
            proStatusFlow.value = ProStatus.Pro

            vm.isLimitReached.test {
                assertThat(awaitItem()).isFalse()
            }
        }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.ui.dictionary.DictionaryViewModelTest" --no-build-cache 2>&1 | tail -20`
Expected: FAIL — `DictionaryViewModel` not found

**Step 3: Write minimal implementation**

```kotlin
package com.voxink.app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxink.app.billing.BillingManager
import com.voxink.app.data.local.DictionaryEntry
import com.voxink.app.data.repository.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel
    @Inject
    constructor(
        private val repository: DictionaryRepository,
        private val billingManager: BillingManager,
    ) : ViewModel() {
        val entries: StateFlow<List<DictionaryEntry>> =
            repository.getAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val count: StateFlow<Int> =
            repository.count()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        val isPro: StateFlow<Boolean> =
            billingManager.proStatus
                .map { it.isPro }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val isLimitReached: StateFlow<Boolean> =
            combine(count, isPro) { c, pro ->
                !pro && c >= FREE_DICTIONARY_LIMIT
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        private val _showDuplicateToast = MutableStateFlow(false)
        val showDuplicateToast: StateFlow<Boolean> = _showDuplicateToast.asStateFlow()

        fun addWord(word: String) {
            if (word.isBlank()) return
            viewModelScope.launch {
                val result = repository.add(word)
                if (result == -1L) {
                    _showDuplicateToast.value = true
                }
            }
        }

        fun removeWord(entry: DictionaryEntry) {
            viewModelScope.launch {
                repository.remove(entry)
            }
        }

        fun dismissDuplicateToast() {
            _showDuplicateToast.value = false
        }

        companion object {
            const val FREE_DICTIONARY_LIMIT = 10
        }
    }
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.voxink.app.ui.dictionary.DictionaryViewModelTest" --no-build-cache 2>&1 | tail -20`
Expected: PASS (9 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/dictionary/DictionaryViewModel.kt app/src/test/java/com/voxink/app/ui/dictionary/DictionaryViewModelTest.kt
git commit -m "feat: add DictionaryViewModel with Pro limit enforcement"
```

---

### Task 12: DictionaryScreen Compose UI

**Files:**
- Create: `app/src/main/java/com/voxink/app/ui/dictionary/DictionaryScreen.kt`

**Step 1: Write the Composable**

```kotlin
package com.voxink.app.ui.dictionary

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreenContent(
    onNavigateBack: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val count by viewModel.count.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val isLimitReached by viewModel.isLimitReached.collectAsState()
    val showDuplicate by viewModel.showDuplicateToast.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val limit = DictionaryViewModel.FREE_DICTIONARY_LIMIT

    LaunchedEffect(showDuplicate) {
        if (showDuplicate) {
            Toast.makeText(context, context.getString(R.string.dictionary_duplicate), Toast.LENGTH_SHORT).show()
            viewModel.dismissDuplicateToast()
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.dictionary_title)) },
            text = { Text(stringResource(R.string.dictionary_help)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionary_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(stringResource(R.string.dictionary_add_hint)) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    enabled = !isLimitReached,
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.addWord(inputText)
                        inputText = ""
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = !isLimitReached && inputText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.dictionary_add_button))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Count display
            Text(
                text = if (isPro) {
                    stringResource(R.string.dictionary_count_unlimited, count)
                } else {
                    stringResource(R.string.dictionary_count, count, limit)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Upgrade banner
            if (isLimitReached) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        stringResource(R.string.dictionary_upgrade),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (entries.isEmpty()) {
                // Empty state
                Text(
                    stringResource(R.string.dictionary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                LazyColumn {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.word,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { viewModel.removeWord(entry) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/dictionary/DictionaryScreen.kt
git commit -m "feat: add DictionaryScreen Compose UI"
```

---

### Task 13: Wire Navigation — Settings Entry Point + NavHost Route

**Files:**
- Modify: `app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/voxink/app/ui/MainActivity.kt`

**Step 1: Add dictionary entry point to SettingsScreenContent**

In `SettingsScreen.kt`, modify the `SettingsScreenContent` function signature to accept navigation callback:

```kotlin
fun SettingsScreenContent(
    onNavigateBack: () -> Unit,
    onNavigateToDictionary: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
```

After the `RefinementSection` (line 111) and before `PermissionSection` (line 113), add:

```kotlin
HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
DictionaryEntryRow(state, onNavigateToDictionary)
```

Add the composable function:

```kotlin
@Composable
private fun DictionaryEntryRow(
    state: SettingsUiState,
    onNavigate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.dictionary_title),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Note: The dictionary count display (e.g., "3/10") would require adding DictionaryRepository to SettingsViewModel. To keep this simple, the count is shown only on the Dictionary screen itself. The Settings row is just a navigation entry.

**Step 2: Add "dictionary" route to NavHost in MainActivity.kt**

Add the import:

```kotlin
import com.voxink.app.ui.dictionary.DictionaryScreenContent
```

Modify the `composable("settings")` block to pass the navigation callback:

```kotlin
composable("settings") {
    SettingsScreenContent(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDictionary = { navController.navigate("dictionary") },
    )
}
```

Add the new route after the "transcription" route:

```kotlin
composable("dictionary") {
    DictionaryScreenContent(onNavigateBack = { navController.popBackStack() })
}
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/voxink/app/ui/settings/SettingsScreen.kt app/src/main/java/com/voxink/app/ui/MainActivity.kt
git commit -m "feat: add dictionary navigation from Settings and NavHost route"
```

---

### Task 14: Full Test Suite + Build Verification

**Files:** None (verification only)

**Step 1: Run all unit tests**

Run: `./gradlew testDebugUnitTest --no-build-cache 2>&1 | tail -30`
Expected: ALL PASS

**Step 2: Run full debug build**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 3: Run lint and ktlint**

Run: `./gradlew ktlintCheck 2>&1 | tail -20`
Expected: PASS (or fix any formatting issues with `./gradlew ktlintFormat`)

**Step 4: If any failures, fix and re-run**

**Step 5: Final commit (if any formatting fixes)**

```bash
git add -u
git commit -m "style: fix formatting for custom vocabulary feature"
```

---

## Summary

| Task | Description | New Files | Modified Files |
|------|-------------|-----------|----------------|
| 1 | DictionaryEntry Room entity | 2 | 0 |
| 2 | DictionaryDao | 1 | 0 |
| 3 | AppDatabase migration + DI | 0 | 2 |
| 4 | DictionaryRepository | 2 | 0 |
| 5 | VocabularyPromptBuilder utility | 2 | 0 |
| 6 | Thread vocabulary through STT | 0 | 4 |
| 7 | Thread vocabulary through LLM | 0 | 5 |
| 8 | Wire vocabulary into RecordingController | 0 | 2 |
| 9 | Update VoxInkIME entry point | 0 | 1 |
| 10 | i18n strings | 0 | 2 |
| 11 | DictionaryViewModel | 2 | 0 |
| 12 | DictionaryScreen Compose UI | 1 | 0 |
| 13 | Navigation wiring | 0 | 2 |
| 14 | Full test suite verification | 0 | 0 |

**Total: 10 new files, 18 modified files, 14 tasks**
