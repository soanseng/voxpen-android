package com.voxpen.app.data.model

sealed class ToneStyle(
    val key: String,
    val emoji: String,
) {
    data object Casual : ToneStyle("casual", "\uD83D\uDCAC")           // 💬
    data object Professional : ToneStyle("professional", "\uD83D\uDCBC") // 💼
    data object Email : ToneStyle("email", "\uD83D\uDCE7")              // 📧
    data object Note : ToneStyle("note", "\uD83D\uDCDD")                // 📝
    data object Social : ToneStyle("social", "\uD83D\uDCF1")            // 📱
    data object Custom : ToneStyle("custom", "⚙")

    companion object {
        val DEFAULT: ToneStyle get() = Casual

        val all: List<ToneStyle> get() = listOf(Casual, Professional, Email, Note, Social, Custom)

        fun fromKey(key: String): ToneStyle =
            when (key) {
                "casual" -> Casual
                "professional" -> Professional
                "email" -> Email
                "note" -> Note
                "social" -> Social
                "custom" -> Custom
                else -> DEFAULT
            }
    }
}
