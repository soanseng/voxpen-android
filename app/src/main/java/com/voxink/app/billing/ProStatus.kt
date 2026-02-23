package com.voxink.app.billing

sealed interface ProStatus {
    data object Free : ProStatus

    data object Pro : ProStatus

    val isPro: Boolean
        get() = this is Pro
}
