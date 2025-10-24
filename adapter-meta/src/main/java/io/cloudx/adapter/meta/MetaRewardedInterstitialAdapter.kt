package io.cloudx.adapter.meta

import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.RewardedInterstitialAd
import com.facebook.ads.RewardedInterstitialAdListener
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.toCloudXError

@Keep
internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {

    override fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXRewardedInterstitialAdapterListener,
    ): Result<CloudXRewardedInterstitialAdapter, String> = Result.Success(
        MetaRewardedInterstitialAdapter(
            contextProvider = contextProvider,
            placementName = placementName,
            serverExtras = serverExtras,
            adm = adm,
            listener = listener
        )
    )
}

internal class MetaRewardedInterstitialAdapter(
    private val contextProvider: ContextProvider,
    placementName: String,
    private val serverExtras: Bundle,
    private val adm: String,
    private var listener: CloudXRewardedInterstitialAdapterListener?
) : CloudXRewardedInterstitialAdapter, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private val logger = CXLogger.forPlacement("MetaRewardedInterstitialAdapter", placementName)
    private var ad: RewardedInterstitialAd? = null

    override fun load() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        if (metaPlacementId.isNullOrEmpty()) {
            val message = "Meta placement ID is null or empty"
            logger.e(message)
            listener?.onError(CloudXErrorCode.ADAPTER_INVALID_SERVER_EXTRAS.toCloudXError(message = message))
            return
        }

        val ad = RewardedInterstitialAd(contextProvider.getContext(), metaPlacementId)
        this.ad = ad

        ad.loadAd(
            ad.buildLoadAdConfig().withAdListener(object : RewardedInterstitialAdListener {
                override fun onError(ad: Ad?, adError: AdError?) {
                    listener?.onError(adError.toCloudXError())
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
                .build()
        )
    }

    override fun show() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        val ad = this.ad
        if (ad == null || !ad.isAdLoaded) {
            logger.w("Rewarded Interstitial ad not ready to show for placement: $metaPlacementId (ad not loaded)")
            listener?.onError(
                CloudXErrorCode.ADAPTER_INVALID_LOAD_STATE.toCloudXError(
                    message = "Rewarded Interstitial ad is not loaded"
                )
            )
            return
        }

        // Check if the ad is already expired or invalidated, and do not show ad if that is the case
        if (ad.isAdInvalidated) {
            logger.w("Rewarded Interstitial ad invalidated for Meta placement: $metaPlacementId")
            listener?.onError(CloudXErrorCode.ADAPTER_INVALID_LOAD_STATE.toCloudXError(message = "Rewarded Interstitial ad is invalidated"))
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