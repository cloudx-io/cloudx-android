package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.CloudXAdRevenueListener
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.CXSdk
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.initialization.InitializationState
import io.cloudx.sdk.toCloudXError
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CXInterstitialAd(
    placementName: String,
) : CloudXInterstitialAd {

    private val logger = CXLogger.forPlacement("CXInterstitialAd", placementName)
    private val initJob: Job
    private var interstitial: CloudXInterstitialAd? = null

    init {
        initJob = MainScope().launch {
            val initState = CXSdk.initState.first { it is InitializationState.Initialized }
                    as InitializationState.Initialized
            interstitial = initState.initializationService.adFactory!!.createInterstitial(
                AdFactory.CreateAdParams(placementName)
            )
            interstitial?.listener = listener
            interstitial?.revenueListener = revenueListener
            interstitial?.load()
        }
    }

    override var listener: CloudXInterstitialListener? = null
        set(value) {
            field = value
            interstitial?.listener = value
        }

    override var revenueListener: CloudXAdRevenueListener? = null
        set(value) {
            field = value
            interstitial?.revenueListener = value
        }

    override val isAdReady: Boolean
        get() = interstitial?.isAdReady ?: false

    override fun load() {
        if (interstitial != null) {
            interstitial?.load()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            val error = CloudXErrorCode.NOT_INITIALIZED.toCloudXError()
            logger.e(error.effectiveMessage)
            listener?.onAdLoadFailed(error)
        }
    }

    override fun show() {
        if (interstitial != null) {
            interstitial?.show()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            val error = CloudXErrorCode.NOT_INITIALIZED.toCloudXError()
            logger.e(error.effectiveMessage)
            listener?.onAdDisplayFailed(error)
        }
    }

    override fun destroy() {
        initJob.cancel()
        interstitial?.destroy()
    }
}
