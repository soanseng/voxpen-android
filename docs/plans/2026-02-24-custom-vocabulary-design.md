# Custom Vocabulary (Dictionary) Feature Design

**Date**: 2026-02-24
**Status**: Approved

## Overview

Users can maintain a personal vocabulary list of proper nouns, names, places, jargon, and frequently misrecognized words. The vocabulary biases both STT recognition (Whisper prompt) and LLM refinement (system prompt injection) to improve accuracy for user-specific terminology.

Inspired by Wispr Flow and Typeless dictionary features. Key difference: VoxInk targets Chinese homophones (同音不同字) as a primary use case — names like 語墨 being misrecognized as 語末.

## Decisions

| Decision | Choice |
|----------|--------|
| Injection layer | Both STT (Whisper prompt bias) + LLM (system prompt vocabulary) |
| Entry structure | Simple word list (just strings, no categories/metadata) |
| Input method | Manual only for v1 |
| Pricing | Free: up to 10 entries, Pro: unlimited |
| Storage | Room entity (future-proof for import/export, auto-learning) |
| UI | Dedicated Dictionary screen, accessed from Settings |

## Data Layer

### Room Entity

```kotlin
@Entity(tableName = "dictionary_entries")
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val createdAt: Long,
)
```

- `createdAt` for sort order (newest first) and Whisper prompt priority (recent = likely more relevant)
- `UNIQUE` constraint on `word` via `@Insert(onConflict = IGNORE)`

### DAO

```kotlin
@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_entries ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DictionaryEntry>>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    fun count(): Flow<Int>

    @Insert(onConflict = IGNORE)
    suspend fun insert(entry: DictionaryEntry)

    @Delete
    suspend fun delete(entry: DictionaryEntry)

    @Query("SELECT word FROM dictionary_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getWords(limit: Int = 500): List<String>
}
```

- `getAll()` + `count()`: Flow for reactive UI
- `getWords(limit)`: non-Flow, called once per recording for prompt construction

### Repository

```kotlin
class DictionaryRepository @Inject constructor(private val dao: DictionaryDao) {
    fun getAll(): Flow<List<DictionaryEntry>> = dao.getAll()
    fun count(): Flow<Int> = dao.count()
    suspend fun add(word: String) = dao.insert(
        DictionaryEntry(word = word.trim(), createdAt = System.currentTimeMillis())
    )
    suspend fun remove(entry: DictionaryEntry) = dao.delete(entry)
    suspend fun getWords(limit: Int = 500): List<String> = dao.getWords(limit)
}
```

### DB Migration

`AppDatabase` version 1 → 2: `CREATE TABLE dictionary_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, word TEXT NOT NULL, createdAt INTEGER NOT NULL)`

Add unique index on `word` column.

## Prompt Injection

### Whisper STT — prompt parameter

Append vocabulary to existing language prompt:

```
繁體中文轉錄。語墨, Anthropic, Claude, 台北101
```

Constraints:
- Whisper prompt has ~224 token implicit limit
- Base prompt uses ~10-20 tokens, leaving ~200 tokens for vocabulary
- ~60-80 Chinese words or ~100+ English words fit
- Use `getWords(limit = 80)`, join with `, `
- Token estimation heuristic: CJK char = 2 tokens, Latin char = 0.25 tokens
- Truncate from end if over budget (oldest entries dropped first)

**Modified**: `SttRepository.transcribe()` gains `vocabularyHint: String?` parameter. `TranscribeAudioUseCase` assembles the hint string.

### LLM Refinement — system prompt suffix

Append vocabulary list to static refinement prompt:

Chinese:
```
術語表（請優先使用這些詞彙）：語墨, Anthropic, Claude, 台北101
```

English:
```
Vocabulary (prefer these terms): VoxInk, Anthropic, Claude
```

Constraints:
- Full vocabulary list injected (up to 500 entries)
- 500 words ≈ 2000-3000 tokens, negligible for 128K context LLMs
- Latency increase ~50-100ms, imperceptible

**Modified**: `LlmRepository.refine()` gains `vocabulary: List<String>` parameter. Non-empty list triggers suffix append.

### Data Flow

```
RecordingController.onStopRecording()
  → dictionaryRepository.getWords()        // one query, get word list
  → transcribeUseCase(... vocabularyHint)   // pass to STT
  → refineTextUseCase(... vocabulary)       // pass to LLM
```

## UI

### Settings Entry Point

New row in Settings below "Text Refinement" section:

```
Custom Vocabulary        [3/10] >
自定義詞彙
```

Shows current count / limit. Tapping navigates to Dictionary screen.

### Dictionary Screen

```
┌─────────────────────────────────┐
│  ← Custom Vocabulary      [?]  │  TopAppBar + help icon
├─────────────────────────────────┤
│  ┌───────────────────┐ [新增]  │  TextField + Add button
│  │ 輸入新詞彙...      │         │
│  └───────────────────┘         │
├─────────────────────────────────┤
│  3 / 10 個詞彙                  │  Count (Free shows limit)
├─────────────────────────────────┤
│  語墨                      ✕   │  Entries, newest first
│  Anthropic                 ✕   │
│  台北101                   ✕   │
└─────────────────────────────────┘
```

Interactions:
- **Add**: TextField + button, Enter or tap to submit. Auto-clear and re-focus for rapid entry.
- **Delete**: X icon button per row, no confirmation needed (easy to re-add).
- **Duplicate**: Toast "Word already exists" (Room IGNORE handles data layer).
- **Limit reached**: TextField disabled + upgrade banner for Free users.
- **Empty state**: Explanatory text about adding names/places to improve accuracy.
- **Sort**: newest first (`createdAt DESC`).

### Navigation

New `"dictionary"` route in existing NavHost. Settings screen navigates via `navController.navigate("dictionary")`.

## Pro Limit Enforcement

```
FREE_DICTIONARY_LIMIT = 10
```

- `DictionaryViewModel` holds `count: StateFlow<Int>` + `isProUser: StateFlow<Boolean>`
- Add gate: `if (!isPro && count >= 10)` → disable input, show upgrade banner
- **Downgrade behavior**: Existing entries preserved, all injected into prompts. Only new additions blocked.
- Uses existing `BillingManager.isPro` Flow.

## i18n Strings

| Key | en | zh-rTW |
|-----|-----|--------|
| `dictionary_title` | Custom Vocabulary | 自定義詞彙 |
| `dictionary_add_hint` | Add a word... | 輸入新詞彙... |
| `dictionary_add_button` | Add | 新增 |
| `dictionary_count` | %1$d / %2$d words | %1$d / %2$d 個詞彙 |
| `dictionary_count_unlimited` | %1$d words | %1$d 個詞彙 |
| `dictionary_empty` | Add names, places, and terms to improve voice recognition accuracy | 加入人名、地名、專有名詞，提升語音辨識準確度 |
| `dictionary_duplicate` | Word already exists | 此詞彙已存在 |
| `dictionary_upgrade` | Upgrade to Pro for unlimited vocabulary | 升級 Pro 版解鎖無限詞彙 |
| `dictionary_help` | Words you add here help the voice engine recognize names, places, and special terms more accurately. | 加入的詞彙會協助語音引擎更準確地辨識人名、地名與專有名詞。 |

## Future Work (deferred)

- **Import/Export**: Read/write vocabulary from text file or CSV for backup and sharing
- **Auto-learning**: Detect user corrections post-transcription, suggest adding misrecognized words to dictionary
- **Team sharing**: Export vocabulary lists for team distribution (Wispr Flow-style)
- **Frequency-based priority**: Track which vocabulary entries are most used, prioritize in Whisper prompt

## Files Impacted

| File | Change |
|------|--------|
| `data/local/DictionaryEntry.kt` | **New** — Room entity |
| `data/local/DictionaryDao.kt` | **New** — DAO interface |
| `data/local/AppDatabase.kt` | **Modify** — add entity, version bump, migration |
| `data/repository/DictionaryRepository.kt` | **New** — repository |
| `data/repository/SttRepository.kt` | **Modify** — add `vocabularyHint` param |
| `data/repository/LlmRepository.kt` | **Modify** — add `vocabulary` param, prompt suffix |
| `data/model/RefinementPrompt.kt` | **Modify** — add vocabulary suffix builder |
| `domain/usecase/TranscribeAudioUseCase.kt` | **Modify** — thread vocabulary hint |
| `domain/usecase/RefineTextUseCase.kt` | **Modify** — thread vocabulary list |
| `ime/RecordingController.kt` | **Modify** — fetch vocabulary, pass to use cases |
| `ui/dictionary/DictionaryScreen.kt` | **New** — dictionary management UI |
| `ui/dictionary/DictionaryViewModel.kt` | **New** — ViewModel |
| `ui/settings/SettingsScreen.kt` | **Modify** — add dictionary entry point row |
| `ui/MainActivity.kt` | **Modify** — add dictionary nav route |
| `di/AppModule.kt` or `DatabaseModule.kt` | **Modify** — provide DictionaryDao, DictionaryRepository |
| `res/values/strings.xml` | **Modify** — add i18n strings |
| `res/values-zh-rTW/strings.xml` | **Modify** — add i18n strings |
