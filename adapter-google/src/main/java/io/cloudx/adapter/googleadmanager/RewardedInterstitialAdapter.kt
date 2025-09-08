package io.cloudx.adapter.googleadmanager

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider

internal class RewardedInterstitialAdapter(
    private val contextProvider: ContextProvider,
    private val adUnitId: String,
    private var listener: CloudXRewardedInterstitialAdapterListener?
) : CloudXRewardedInterstitialAdapter, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private var ad: RewardedInterstitialAd? = null

    override fun load() {
        RewardedInterstitialAd.load(
            contextProvider.getContext(),
            adUnitId,
            AdManagerAdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    super.onAdFailedToLoad(p0)
                    listener?.onError(CloudXAdapterError(description = p0.toString()))
                }

                override fun onAdLoaded(p0: RewardedInterstitialAd) {
                    super.onAdLoaded(p0)

                    ad = p0
                    listener?.onLoad()
                }
            })
    }

    override fun show() {
        val ad = this.ad
        if (ad == null) {
            listener?.onError(CloudXAdapterError(description = "can't show: ad is not loaded"))
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                listener?.onError(CloudXAdapterError(description = adError.message))
            }

            override fun onAdShowedFullScreenContent() {
                listener?.onShow()
            }

            override fun onAdImpression() {
                listener?.onImpression()
            }

            override fun onAdClicked() {
                listener?.onClick()
            }

            override fun onAdDismissedFullScreenContent() {
                listener?.onHide()
            }
        }

        // Ad will still successfully show even with a null Activity
        GoogleAdHelper.showRewardedInterstitial(ad, contextProvider.getActivityOrNull(), OnUserEarnedRewardListener {
            listener?.onEligibleForReward()
        })
    }

    override fun destroy() {
        ad = null
        listener = null
    }
}