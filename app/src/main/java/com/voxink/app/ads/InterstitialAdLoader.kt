package com.voxink.app.ads

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdLoader
    @Inject
    constructor() {
        private var interstitialAd: InterstitialAd? = null
        private var lastShowTimeMs: Long = 0
        private var showCountToday: Int = 0
        private var installTimeMs: Long = System.currentTimeMillis()

        fun preload(activity: Activity) {
            if (interstitialAd != null) return
            InterstitialAd.load(
                activity,
                AdManager.INTERSTITIAL_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        Timber.d("Interstitial ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        Timber.w("Interstitial ad failed to load: ${error.message}")
                    }
                },
            )
        }

        fun show(
            activity: Activity,
            onDismissed: () -> Unit = {},
        ): Boolean {
            if (!canShow()) return false
            val ad = interstitialAd ?: return false

            ad.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        preload(activity)
                        onDismissed()
                    }
                }

            ad.show(activity)
            lastShowTimeMs = System.currentTimeMillis()
            showCountToday++
            return true
        }

        private fun canShow(): Boolean {
            val now = System.currentTimeMillis()
            // No interstitials for first 3 days
            if (now - installTimeMs < GRACE_PERIOD_MS) return false
            // Max 3 per day
            if (showCountToday >= MAX_DAILY_SHOWS) return false
            // Min 5 min between shows
            if (now - lastShowTimeMs < MIN_INTERVAL_MS) return false
            return true
        }

        fun setInstallTime(timeMs: Long) {
            installTimeMs = timeMs
        }

        companion object {
            const val MIN_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
            const val MAX_DAILY_SHOWS = 3
            const val GRACE_PERIOD_MS = 3 * 24 * 60 * 60 * 1000L // 3 days
        }
    }
