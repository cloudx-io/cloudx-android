package io.cloudx.adapter.meta

import android.app.Activity
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.RewardedInterstitialAd
import com.facebook.ads.RewardedInterstitialAdListener
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitial
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialListener

internal class RewardedInterstitialAdapter(
    private val activity: Activity,
    private val adUnitId: String,
    private var listener: CloudXRewardedInterstitialListener?
) : CloudXRewardedInterstitial, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private var ad: RewardedInterstitialAd? = null

    override fun load() {
        val ad = RewardedInterstitialAd(activity, adUnitId)
        this.ad = ad

        ad.loadAd(ad.buildLoadAdConfig().withAdListener(object : RewardedInterstitialAdListener {
            override fun onError(p0: Ad?, p1: AdError?) {
                listener?.onError(CloudXAdapterError(description = p1?.errorMessage ?: ""))
            }

            override fun onAdLoaded(p0: Ad?) {
                listener?.onLoad()
            }

            override fun onAdClicked(p0: Ad?) {
                listener?.onClick()
            }

            override fun onLoggingImpression(p0: Ad?) {
                listener?.onImpression()
            }

            override fun onRewardedInterstitialCompleted() {
                listener?.onEligibleForReward()
            }

            override fun onRewardedInterstitialClosed() {
                listener?.onHide()
            }


        }).build())
    }

    override fun show() {
        val ad = this.ad
        if (ad == null || !ad.isAdLoaded) {
            listener?.onError(CloudXAdapterError(description = "can't show: ad is not loaded"))
            return
        }

        // Check if ad is already expired or invalidated, and do not show ad if that is the case. You will not get paid to show an invalidated ad.
        if (ad.isAdInvalidated) {
            listener?.onError(CloudXAdapterError(description = "can't show: ad is invalidated"))
            return
        }

        // Show the ad
        ad.show()
        listener?.onShow()
    }

    override fun destroy() {
        ad?.destroy()
        ad = null
        listener = null
    }
}