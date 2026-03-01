package com.voxpen.app.data.model

object BuiltinAppToneTable {
    val rules: Map<String, ToneStyle> = mapOf(
        // Casual — messaging apps
        "com.whatsapp" to ToneStyle.Casual,
        "org.telegram.messenger" to ToneStyle.Casual,
        "com.facebook.orca" to ToneStyle.Casual,
        "jp.naver.line.android" to ToneStyle.Casual,
        "com.discord" to ToneStyle.Casual,
        "com.kakao.talk" to ToneStyle.Casual,
        "com.viber.voip" to ToneStyle.Casual,
        // Email
        "com.google.android.gm" to ToneStyle.Email,
        "com.microsoft.office.outlook" to ToneStyle.Email,
        "me.proton.android.mail" to ToneStyle.Email,
        // Professional — work collaboration
        "com.slack" to ToneStyle.Professional,
        "com.microsoft.teams" to ToneStyle.Professional,
        // Note — personal note-taking
        "com.google.android.keep" to ToneStyle.Note,
        "com.notion.id" to ToneStyle.Note,
        "md.obsidian" to ToneStyle.Note,
        "com.evernote" to ToneStyle.Note,
        // Social — public social media
        "com.twitter.android" to ToneStyle.Social,
        "com.instagram.android" to ToneStyle.Social,
        "com.instagram.threads" to ToneStyle.Social,
        "com.zhiliaoapp.musically" to ToneStyle.Social,
        "com.facebook.katana" to ToneStyle.Social,
        "com.dcard.app" to ToneStyle.Social,
    )
}
