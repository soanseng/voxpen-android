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

        val isLoaded: Boolean
            get() = rewardedAd != null

        fun preload(activity: Activity) {
            if (rewardedAd != null) return
            RewardedAd.load(
                activity,
                AdManager.REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        Timber.d("Rewarded ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        Timber.w("Rewarded ad failed to load: ${error.message}")
                    }
                },
            )
        }

        fun show(
            activity: Activity,
            onRewarded: (Int) -> Unit,
        ): Boolean {
            val ad = rewardedAd ?: return false

            ad.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        preload(activity)
                    }
                }

            ad.show(activity) { reward ->
                Timber.d("User earned reward: ${reward.amount} ${reward.type}")
                onRewarded(reward.amount)
            }
            return true
        }
    }
