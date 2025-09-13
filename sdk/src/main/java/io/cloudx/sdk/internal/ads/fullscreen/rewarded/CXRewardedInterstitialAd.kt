package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXIsAdLoadedListener
import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialListener
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.CXSDK
import io.cloudx.sdk.internal.initialization.InitializationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CXRewardedInterstitialAd(
    private val placementName: String,
    private val listener: CloudXRewardedInterstitialListener?
) : CloudXRewardedInterstitialAd {

    private val TAG = "CXRewardedInterstitialAd"
    private val initJob: Job
    private var rewardedInterstitial: CloudXRewardedInterstitialAd? = null
    private var isAdLoadedListener: CloudXIsAdLoadedListener? = null

    init {
        initJob = MainScope().launch {
            val initState = CXSDK.initState.first { it is InitializationState.Initialized }
                    as InitializationState.Initialized
            rewardedInterstitial = initState.initializationService.adFactory!!.createRewarded(
                AdFactory.CreateAdParams(placementName, listener)
            )
            rewardedInterstitial?.setIsAdLoadedListener(isAdLoadedListener)
            rewardedInterstitial?.load()
        }
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

        if (CXSDK.initState.value is InitializationState.Uninitialized) {
            CloudXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdLoadFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
    }

    override fun show() {
        if (rewardedInterstitial != null) {
            rewardedInterstitial?.show()
            return
        }

        if (CXSDK.initState.value is InitializationState.Uninitialized) {
            CloudXLogger.e(TAG, "CloudX SDK is uninitialized")
            listener?.onAdDisplayFailed(CloudXAdError("CloudX SDK is uninitialized"))
        }
    }

    override fun destroy() {
        initJob.cancel()
        rewardedInterstitial?.destroy()
    }
}
