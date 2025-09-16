package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXRewardedInterstitialListener
import io.cloudx.sdk.internal.CXLogger

class RewardedFragment : FullPageAdFragment() {

    override fun createAd(listener: CloudXAdListener) = CloudX.createRewardedInterstitial(
        placementName,
        object : CloudXRewardedInterstitialListener, CloudXAdListener by listener {
            override fun onUserRewarded(cloudXAd: CloudXAd) {
                CXLogger.i(
                    logTag,
                    "REWARD; placement: $placementName; network: ${cloudXAd.bidderName}"
                )
            }
        }
    )

    override val adType: String = "Rewarded"
    override val logTag: String = "RewardedFragment"
}