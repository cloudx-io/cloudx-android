package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.CloudXIsAdLoadedListener
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.initialization.InitializationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CXInterstitialAd(
    private val placementName: String,
    private val listener: CloudXInterstitialListener?
) : CloudXInterstitialAd {

    private val TAG = "CloudXInterstitialAd"
    private val initJob: Job
    private var interstitial: CloudXInterstitialAd? = null
    private var isAdLoadedListener: CloudXIsAdLoadedListener? = null

    init {
        initJob = MainScope().launch {
            val initState = CloudX.initState.first { it is InitializationState.Initialized }
                    as InitializationState.Initialized
            interstitial = initState.initializationService.adFactory!!.createInterstitial(
                AdFactory.CreateAdParams(placementName, listener)
            )
            interstitial?.setIsAdLoadedListener(isAdLoadedListener)
            interstitial?.load()
        }
    }

    override val isAdLoaded: Boolean
        get() = interstitial?.isAdLoaded ?: false

    override fun setIsAdLoadedListener(listener: CloudXIsAdLoadedListener?) {
        isAdLoadedListener = listener
        interstitial?.setIsAdLoadedListener(listener)
    }

    override fun load() {
        if (CloudX.initState.value is InitializationState.Uninitialized) {
            CloudXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdLoadFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
        interstitial?.load()
    }

    override fun show() {
        if (CloudX.initState.value is InitializationState.Uninitialized) {
            CloudXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdDisplayFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
        interstitial?.show()
    }

    override fun destroy() {
        initJob.cancel()
        interstitial?.destroy()
    }
}
