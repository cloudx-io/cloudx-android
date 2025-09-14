package io.cloudx.adapter.meta

import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

@Keep
internal object InterstitialFactory :
    CloudXInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {

    override fun create(
        contextProvider: ContextProvider,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXInterstitialAdapterListener,
    ): Result<CloudXInterstitialAdapter, String> = Result.Success(
        MetaInterstitialAdapter(
            contextProvider = contextProvider,
            serverExtras = serverExtras,
            adm = adm,
            listener = listener
        )
    )
}

internal class MetaInterstitialAdapter(
    private val contextProvider: ContextProvider,
    private val serverExtras: Bundle,
    private val adm: String,
    private var listener: CloudXInterstitialAdapterListener?
) : CloudXInterstitialAdapter, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private val TAG = "MetaInterstitialAdapter"
    private var interstitialAd: InterstitialAd? = null

    override fun load() {
        val placementId = serverExtras.getPlacementId()
        if (placementId == null) {
            CloudXLogger.e(TAG, "Placement ID is null")
            listener?.onError(CloudXAdapterError(description = "Placement ID is null"))
            return
        }

        // Check if an ad is already loaded
        val existingInterstitialAd = this.interstitialAd
        if (checkAndHandleAdReady(placementId, existingInterstitialAd)) {
            return
        }

        val interstitialAd = InterstitialAd(contextProvider.getContext(), placementId)
        this.interstitialAd = interstitialAd

        // Check if the newly constructed ad is already loaded
        if (checkAndHandleAdReady(placementId, interstitialAd)) {
            return
        }

        CloudXLogger.d(TAG, "Loading interstitial ad for placement: $placementId")
        interstitialAd.loadAd(
            interstitialAd.buildLoadAdConfig()
                .withAdListener(createAdListener(placementId, listener))
                .withBid(adm)
                .build()
        )
    }

    override fun show() {
        val placementId = serverExtras.getPlacementId()
        CloudXLogger.d(TAG, "Attempting to show interstitial ad for placement: $placementId")
        val ad = this.interstitialAd
        if (ad == null || !ad.isAdLoaded) {
            CloudXLogger.w(
                TAG,
                "Interstitial ad not ready to show for placement: $placementId (ad not loaded)"
            )
            listener?.onError(CloudXAdapterError(description = "Interstitial ad is not loaded"))
            return
        }

        // Check if ad is already expired or invalidated, and do not show ad if that is the case. You will not get paid to show an invalidated ad.
        if (ad.isAdInvalidated) {
            CloudXLogger.w(TAG, "Interstitial ad invalidated for placement: $placementId")
            listener?.onError(CloudXAdapterError(description = "Interstitial ad is invalidated"))
            return
        }

        // Show the ad
        CloudXLogger.d(TAG, "Showing interstitial ad for placement: $placementId")
        ad.show()
    }

    override fun destroy() {
        val placementId = serverExtras.getPlacementId()
        CloudXLogger.d(TAG, "Destroying interstitial ad for placement: $placementId")
        interstitialAd?.destroy()
        interstitialAd = null
        listener = null
    }

    private fun createAdListener(
        placementId: String,
        listener: CloudXInterstitialAdapterListener?
    ) =
        object : InterstitialAdListener {
            override fun onError(ad: Ad?, adError: AdError?) {
                CloudXLogger.e(
                    TAG,
                    "Interstitial ad failed to load for placement $placementId with error: ${adError?.errorMessage} (${adError?.errorCode})"
                )
                listener?.onError(
                    CloudXAdapterError(
                        description = adError?.errorMessage ?: "Unknown error"
                    )
                )
            }

            override fun onAdLoaded(ad: Ad?) {
                CloudXLogger.d(
                    TAG,
                    "Interstitial ad loaded successfully for placement: $placementId"
                )
                listener?.onLoad()
            }

            override fun onAdClicked(ad: Ad?) {
                CloudXLogger.d(TAG, "Interstitial ad clicked for placement: $placementId")
                listener?.onClick()
            }

            override fun onLoggingImpression(ad: Ad?) {
                CloudXLogger.d(TAG, "Interstitial ad impression logged for placement: $placementId")
                listener?.onImpression()
            }

            override fun onInterstitialDisplayed(ad: Ad?) {
                CloudXLogger.d(TAG, "Interstitial ad displayed for placement: $placementId")
                listener?.onShow()
            }

            override fun onInterstitialDismissed(ad: Ad?) {
                CloudXLogger.d(TAG, "Interstitial ad dismissed for placement: $placementId")
                listener?.onHide()
            }
        }

    /**
     * Helper function to check if an interstitial ad is ready and handle the callback
     * Returns true if ad was ready (and callback was triggered), false otherwise
     */
    private fun checkAndHandleAdReady(placementId: String, ad: InterstitialAd?): Boolean {
        return if (ad != null && ad.isAdLoaded && !ad.isAdInvalidated) {
            CloudXLogger.d(TAG, "Interstitial ad already loaded for placement: $placementId")
            listener?.onLoad()
            true
        } else {
            false
        }
    }
}
