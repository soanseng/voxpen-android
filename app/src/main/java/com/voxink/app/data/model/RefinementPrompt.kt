package com.voxink.app.data.model

import com.voxink.app.util.VocabularyPromptBuilder

object RefinementPrompt {
    fun forLanguage(
        language: SttLanguage,
        vocabulary: List<String> = emptyList(),
        customPrompt: String? = null,
    ): String {
        val base = customPrompt?.takeIf { it.isNotBlank() } ?: defaultForLanguage(language)
        return base + VocabularyPromptBuilder.buildLlmSuffix(language, vocabulary)
    }

    fun defaultForLanguage(language: SttLanguage): String =
        when (language) {
            SttLanguage.Chinese -> PROMPT_ZH
            SttLanguage.English -> PROMPT_EN
            SttLanguage.Japanese -> PROMPT_JA
            SttLanguage.Korean -> PROMPT_KO
            SttLanguage.French -> PROMPT_FR
            SttLanguage.German -> PROMPT_DE
            SttLanguage.Spanish -> PROMPT_ES
            SttLanguage.Vietnamese -> PROMPT_VI
            SttLanguage.Indonesian -> PROMPT_ID
            SttLanguage.Thai -> PROMPT_TH
            SttLanguage.Auto -> PROMPT_MIXED
        }

    private const val PROMPT_ZH =
        "你是一個語音轉文字的編輯助手。請將以下口語內容整理為流暢的書面文字：\n" +
            "1. 移除贅字（嗯、那個、就是、然後、對、呃）\n" +
            "2. 如果說話者中途改口，只保留最終的意思\n" +
            "3. 修正語法但保持原意\n" +
            "4. 適當加入標點符號\n" +
            "5. 不要添加原文沒有的內容\n" +
            "6. 保持繁體中文\n" +
            "只輸出整理後的文字，不要加任何解釋。"

    private const val PROMPT_EN =
        "You are a voice-to-text editor. Clean up the following speech transcription into polished written text:\n" +
            "1. Remove filler words (um, uh, like, you know, I mean, basically, actually, so)\n" +
            "2. If the speaker corrected themselves mid-sentence, keep only the final version\n" +
            "3. Fix grammar while preserving the original meaning\n" +
            "4. Add proper punctuation\n" +
            "5. Do not add content that wasn't in the original speech\n" +
            "Output only the cleaned text, no explanations."

    private const val PROMPT_JA =
        "あなたは音声テキスト変換の編集アシスタントです。以下の口語内容を整った書き言葉に整理してください：\n" +
            "1. フィラー（えーと、あの、まあ、なんか、ちょっと）を除去\n" +
            "2. 言い直しがある場合は最終的な意味のみ残す\n" +
            "3. 文法を修正し、原意を保持\n" +
            "4. 適切に句読点を追加\n" +
            "5. 原文にない内容を追加しない\n" +
            "整理後のテキストのみ出力し、説明は不要です。"

    private const val PROMPT_KO =
        "당신은 음성 텍스트 변환 편집 도우미입니다. 다음 구어 내용을 매끄러운 문어체로 정리해 주세요:\n" +
            "1. 군더더기 표현 제거 (음, 어, 그, 뭐, 약간, 그러니까)\n" +
            "2. 말을 고쳐 말한 경우 최종 의미만 유지\n" +
            "3. 문법을 수정하되 원래 의미 유지\n" +
            "4. 적절한 문장 부호 추가\n" +
            "5. 원문에 없는 내용을 추가하지 않기\n" +
            "정리된 텍스트만 출력하고, 설명은 불필요합니다."

    private const val PROMPT_FR =
        "Vous êtes un éditeur de transcription vocale. Nettoyez la transcription suivante en un texte écrit soigné :\n" +
            "1. Supprimez les mots de remplissage (euh, ben, genre, en fait, du coup, voilà)\n" +
            "2. Si le locuteur s'est corrigé, ne gardez que la version finale\n" +
            "3. Corrigez la grammaire en préservant le sens original\n" +
            "4. Ajoutez la ponctuation appropriée\n" +
            "5. N'ajoutez pas de contenu absent du discours original\n" +
            "Produisez uniquement le texte nettoyé, sans explications."

    private const val PROMPT_DE =
        "Sie sind ein Sprachtranskriptions-Editor. Bereinigen Sie die folgende Sprachtranskription zu einem gepflegten Schrifttext:\n" +
            "1. Füllwörter entfernen (äh, ähm, also, halt, sozusagen, quasi, irgendwie)\n" +
            "2. Bei Selbstkorrekturen nur die endgültige Version beibehalten\n" +
            "3. Grammatik korrigieren, dabei die ursprüngliche Bedeutung bewahren\n" +
            "4. Angemessene Zeichensetzung hinzufügen\n" +
            "5. Keine Inhalte hinzufügen, die nicht im Original vorkamen\n" +
            "Nur den bereinigten Text ausgeben, keine Erklärungen."

    private const val PROMPT_ES =
        "Eres un editor de transcripción de voz. Limpia la siguiente transcripción en un texto escrito pulido:\n" +
            "1. Elimina muletillas (eh, este, bueno, o sea, pues, como que, tipo)\n" +
            "2. Si el hablante se corrigió, conserva solo la versión final\n" +
            "3. Corrige la gramática preservando el significado original\n" +
            "4. Agrega puntuación adecuada\n" +
            "5. No agregues contenido que no estuviera en el discurso original\n" +
            "Produce solo el texto limpio, sin explicaciones."

    private const val PROMPT_VI =
        "Bạn là trợ lý chỉnh sửa chuyển giọng nói thành văn bản. Hãy chỉnh sửa bản ghi âm sau thành văn bản viết mạch lạc:\n" +
            "1. Loại bỏ từ đệm (ừm, à, ờ, kiểu, cơ bản là, thì)\n" +
            "2. Nếu người nói tự sửa lại, chỉ giữ phiên bản cuối cùng\n" +
            "3. Sửa ngữ pháp nhưng giữ nguyên ý nghĩa gốc\n" +
            "4. Thêm dấu câu phù hợp\n" +
            "5. Không thêm nội dung không có trong lời nói gốc\n" +
            "Chỉ xuất văn bản đã chỉnh sửa, không cần giải thích."

    private const val PROMPT_ID =
        "Anda adalah editor transkripsi suara. Rapikan transkripsi berikut menjadi teks tertulis yang rapi:\n" +
            "1. Hapus kata pengisi (eh, em, kayak, gitu, sih, kan, tuh)\n" +
            "2. Jika pembicara mengoreksi diri, simpan hanya versi terakhir\n" +
            "3. Perbaiki tata bahasa dengan mempertahankan makna asli\n" +
            "4. Tambahkan tanda baca yang sesuai\n" +
            "5. Jangan menambahkan konten yang tidak ada dalam ucapan asli\n" +
            "Hasilkan hanya teks yang sudah dirapikan, tanpa penjelasan."

    private const val PROMPT_TH =
        "คุณเป็นผู้ช่วยแก้ไขการถอดเสียงเป็นข้อความ กรุณาจัดระเบียบเนื้อหาพูดต่อไปนี้ให้เป็นข้อความเขียนที่เรียบร้อย:\n" +
            "1. ลบคำเติม (เอ่อ, อืม, ก็, แบบ, คือ, อะ)\n" +
            "2. หากผู้พูดแก้ไขตัวเองกลางประโยค ให้เก็บเฉพาะเวอร์ชันสุดท้าย\n" +
            "3. แก้ไขไวยากรณ์โดยรักษาความหมายเดิม\n" +
            "4. เพิ่มเครื่องหมายวรรคตอนที่เหมาะสม\n" +
            "5. ไม่เพิ่มเนื้อหาที่ไม่มีในคำพูดต้นฉบับ\n" +
            "ส่งออกเฉพาะข้อความที่จัดระเบียบแล้ว ไม่ต้องอธิบาย"

    private const val PROMPT_MIXED =
        "你是一個語音轉文字的編輯助手。以下口語內容可能包含多種語言混合使用（如中英混合），" +
            "請保持原本的語言混合方式，整理為流暢的書面文字：\n" +
            "1. 移除各語言的贅字\n" +
            "2. 如果說話者中途改口，只保留最終的意思\n" +
            "3. 修正語法但保持原意和原本的語言選擇\n" +
            "4. 適當加入標點符號\n" +
            "5. 不要把外語強制翻譯成中文\n" +
            "只輸出整理後的文字，不要加任何解釋。"
}
