package com.voxink.app

import android.app.Application
import com.voxink.app.ads.AdManager
import com.voxink.app.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VoxInkApplication : Application() {
    @Inject lateinit var adManager: AdManager

    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        adManager.initialize()
        billingManager.initialize()
    }
}
