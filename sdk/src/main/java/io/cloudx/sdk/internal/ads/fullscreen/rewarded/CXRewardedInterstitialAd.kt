package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXIsAdLoadedListener
import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialListener
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.CXSdk
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.initialization.InitializationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CXRewardedInterstitialAd(
    private val placementName: String,
) : CloudXRewardedInterstitialAd {

    private val TAG = "CXRewardedInterstitialAd"
    private val initJob: Job
    private var rewardedInterstitial: CloudXRewardedInterstitialAd? = null
    private var isAdLoadedListener: CloudXIsAdLoadedListener? = null

    init {
        initJob = MainScope().launch {
            val initState = CXSdk.initState.first { it is InitializationState.Initialized }
                    as InitializationState.Initialized
            rewardedInterstitial = initState.initializationService.adFactory!!.createRewarded(
                AdFactory.CreateAdParams(placementName)
            )
            rewardedInterstitial?.listener = listener
            rewardedInterstitial?.setIsAdLoadedListener(isAdLoadedListener)
            rewardedInterstitial?.load()
        }
    }

    override var listener: CloudXRewardedInterstitialListener? = null
        set(value) {
            field = value
            rewardedInterstitial?.listener = value
        }

    override val isAdLoaded: Boolean
        get() = rewardedInterstitial?.isAdLoaded ?: false

    override fun setIsAdLoadedListener(listener: CloudXIsAdLoadedListener?) {
        isAdLoadedListener = listener
        rewardedInterstitial?.setIsAdLoadedListener(listener)
    }

    override fun load() {
        if (rewardedInterstitial != null) {
            rewardedInterstitial?.load()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            CXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdLoadFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
    }

    override fun show() {
        if (rewardedInterstitial != null) {
            rewardedInterstitial?.show()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            CXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdDisplayFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
    }

    override fun destroy() {
        initJob.cancel()
        rewardedInterstitial?.destroy()
    }
}
