package io.cloudx.adapter.meta

import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.RewardedInterstitialAd
import com.facebook.ads.RewardedInterstitialAdListener
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider

@Keep
internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {

    override fun create(
        contextProvider: ContextProvider,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXRewardedInterstitialAdapterListener,
    ): Result<CloudXRewardedInterstitialAdapter, String> = Result.Success(
        MetaRewardedInterstitialAdapter(
            contextProvider,
            serverExtras,
            adm = adm,
            listener
        )
    )
}

internal class MetaRewardedInterstitialAdapter(
    private val contextProvider: ContextProvider,
    private val serverExtras: Bundle,
    private val adm: String,
    private var listener: CloudXRewardedInterstitialAdapterListener?
) : CloudXRewardedInterstitialAdapter, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private val TAG = "MetaRewardedInterstitialAdapter"
    private var ad: RewardedInterstitialAd? = null

    override fun load() {
        val placementId = serverExtras.getPlacementId()
        if (placementId == null) {
            log(TAG, "Placement ID is null")
            listener?.onError(CloudXAdapterError(description = "Placement ID is null"))
            return
        }

        val ad = RewardedInterstitialAd(contextProvider.getContext(), placementId)
        this.ad = ad

        ad.loadAd(ad.buildLoadAdConfig().withAdListener(object : RewardedInterstitialAdListener {
            override fun onError(ad: Ad?, adError: AdError?) {
                listener?.onError(CloudXAdapterError(description = adError?.errorMessage ?: ""))
            }

            override fun onAdLoaded(ad: Ad?) {
                listener?.onLoad()
            }

            override fun onAdClicked(ad: Ad?) {
                listener?.onClick()
            }

            override fun onLoggingImpression(ad: Ad?) {
                listener?.onImpression()
            }

            override fun onRewardedInterstitialCompleted() {
                listener?.onEligibleForReward()
            }

            override fun onRewardedInterstitialClosed() {
                listener?.onHide()
            }
        })
            .withBid(adm)
            .build())
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