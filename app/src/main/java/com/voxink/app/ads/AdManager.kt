package com.voxink.app.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var initialized = false

        fun initialize() {
            if (initialized) return
            MobileAds.initialize(context) { initStatus ->
                Timber.d("AdMob initialized: $initStatus")
                initialized = true
            }
        }

        companion object {
            // Test ad unit IDs — replace with production IDs before release
            const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
            const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
            const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        }
    }
