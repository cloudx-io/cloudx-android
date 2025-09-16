package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.CXSdk
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.initialization.InitializationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CXInterstitialAd(
    private val placementName: String,
) : CloudXInterstitialAd {

    private val TAG = "CXInterstitialAd"
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
            interstitial?.load()
        }
    }

    override var listener: CloudXInterstitialListener? = null
        set(value) {
            field = value
            interstitial?.listener = value
        }

    override val isAdLoaded: Boolean
        get() = interstitial?.isAdLoaded ?: false

    override fun load() {
        if (interstitial != null) {
            interstitial?.load()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            CXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdLoadFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
    }

    override fun show() {
        if (interstitial != null) {
            interstitial?.show()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            CXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdDisplayFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
    }

    override fun destroy() {
        initJob.cancel()
        interstitial?.destroy()
    }
}
