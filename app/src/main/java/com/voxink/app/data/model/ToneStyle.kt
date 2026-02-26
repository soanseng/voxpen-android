package com.voxink.app.data.model

sealed class ToneStyle(
    val key: String,
) {
    data object Casual : ToneStyle("casual")
    data object Professional : ToneStyle("professional")
    data object Email : ToneStyle("email")
    data object Note : ToneStyle("note")
    data object Social : ToneStyle("social")
    data object Custom : ToneStyle("custom")

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
