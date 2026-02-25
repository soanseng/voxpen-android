package com.voxink.app.ads

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdLoader
    @Inject
    constructor() {
        private var rewardedAd: RewardedAd? = null
        private var isLoading = false

        val isLoaded: Boolean
            get() = rewardedAd != null

        fun preload(activity: Activity) {
            if (rewardedAd != null || isLoading) return
            isLoading = true
            RewardedAd.load(
                activity,
                AdManager.REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isLoading = false
                        Timber.d("Rewarded ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        isLoading = false
                        Timber.w("Rewarded ad failed to load: ${error.message}")
                    }
                },
            )
        }

        /**
         * Load and show a rewarded ad. If already loaded, shows immediately.
         * Otherwise loads first, then shows when ready.
         *
         * @param onAdNotAvailable called when the ad fails to load or show
         */
        fun loadAndShow(
            activity: Activity,
            onRewarded: (Int) -> Unit,
            onAdNotAvailable: () -> Unit = {},
        ) {
            // If already loaded, show immediately
            val existing = rewardedAd
            if (existing != null) {
                showAd(activity, existing, onRewarded, onAdNotAvailable)
                return
            }

            // Load then show
            isLoading = true
            Timber.d("Loading rewarded ad before showing...")
            RewardedAd.load(
                activity,
                AdManager.REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoading = false
                        rewardedAd = ad
                        Timber.d("Rewarded ad loaded, showing now")
                        showAd(activity, ad, onRewarded, onAdNotAvailable)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoading = false
                        rewardedAd = null
                        Timber.w("Rewarded ad failed to load: ${error.message}")
                        onAdNotAvailable()
                    }
                },
            )
        }

        fun show(
            activity: Activity,
            onRewarded: (Int) -> Unit,
        ): Boolean {
            val ad = rewardedAd ?: return false
            showAd(activity, ad, onRewarded) {}
            return true
        }

        private fun showAd(
            activity: Activity,
            ad: RewardedAd,
            onRewarded: (Int) -> Unit,
            onAdNotAvailable: () -> Unit,
        ) {
            ad.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        preload(activity)
                    }

                    override fun onAdFailedToShowFullScreenContent(
                        adError: com.google.android.gms.ads.AdError,
                    ) {
                        Timber.w("Rewarded ad failed to show: ${adError.message}")
                        rewardedAd = null
                        onAdNotAvailable()
                        preload(activity)
                    }
                }

            ad.show(activity) { reward ->
                Timber.d("User earned reward: ${reward.amount} ${reward.type}")
                onRewarded(reward.amount)
            }
        }
    }
