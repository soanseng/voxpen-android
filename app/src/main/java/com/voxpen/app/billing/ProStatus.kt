package com.voxpen.app.billing

enum class ProSource {
    GOOGLE_PLAY,
    LICENSE_KEY,
}

sealed interface ProStatus {
    data object Free : ProStatus

    data class Pro(val source: ProSource) : ProStatus

    val isPro: Boolean
        get() = this is Pro
}
