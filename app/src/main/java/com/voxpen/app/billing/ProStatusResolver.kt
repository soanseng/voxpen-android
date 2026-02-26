package com.voxpen.app.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProStatusResolver
    @Inject
    constructor(
        billingStatusFlow: StateFlow<ProStatus>,
        licenseStatusFlow: StateFlow<ProStatus>,
        scope: CoroutineScope,
    ) {
        val proStatus: StateFlow<ProStatus> =
            combine(billingStatusFlow, licenseStatusFlow) { billing, license ->
                when {
                    billing.isPro -> billing
                    license.isPro -> license
                    else -> ProStatus.Free
                }
            }.stateIn(scope, SharingStarted.Eagerly, ProStatus.Free)
    }
