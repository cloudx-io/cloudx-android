package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.CloudXAdRevenueListener
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialListener
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.CXSdk
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.initialization.InitializationState
import io.cloudx.sdk.toCloudXError
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

    init {
        initJob = MainScope().launch {
            val initState = CXSdk.initState.first { it is InitializationState.Initialized }
                    as InitializationState.Initialized
            rewardedInterstitial = initState.initializationService.adFactory!!.createRewarded(
                AdFactory.CreateAdParams(placementName)
            )
            rewardedInterstitial?.listener = listener
            rewardedInterstitial?.revenueListener = revenueListener
            rewardedInterstitial?.load()
        }
    }

    override var listener: CloudXRewardedInterstitialListener? = null
        set(value) {
            field = value
            rewardedInterstitial?.listener = value
        }

    override var revenueListener: CloudXAdRevenueListener? = null
        set(value) {
            field = value
            rewardedInterstitial?.revenueListener = value
        }

    override val isAdReady: Boolean
        get() = rewardedInterstitial?.isAdReady ?: false

    override fun load() {
        if (rewardedInterstitial != null) {
            rewardedInterstitial?.load()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            val error = CloudXErrorCode.NOT_INITIALIZED.toCloudXError()
            CXLogger.e(TAG, error.effectiveMessage)
            listener?.onAdLoadFailed(error)
        }
    }

    override fun show() {
        if (rewardedInterstitial != null) {
            rewardedInterstitial?.show()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            val error = CloudXErrorCode.NOT_INITIALIZED.toCloudXError()
            CXLogger.e(TAG, error.effectiveMessage)
            listener?.onAdDisplayFailed(error)
        }
    }

    override fun destroy() {
        initJob.cancel()
        rewardedInterstitial?.destroy()
    }
}
