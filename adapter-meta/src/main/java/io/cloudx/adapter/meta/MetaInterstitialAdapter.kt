package io.cloudx.adapter.meta

import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.toCloudXError

@Keep
internal object InterstitialFactory : CloudXInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {

    override fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXInterstitialAdapterListener,
    ): Result<CloudXInterstitialAdapter, String> = Result.Success(
        MetaInterstitialAdapter(
            contextProvider = contextProvider,
            placementName = placementName,
            serverExtras = serverExtras,
            adm = adm,
            listener = listener
        )
    )
}

internal class MetaInterstitialAdapter(
    private val contextProvider: ContextProvider,
    private val placementName: String,
    private val serverExtras: Bundle,
    private val adm: String,
    private var listener: CloudXInterstitialAdapterListener?
) : CloudXInterstitialAdapter, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private val TAG = "MetaInterstitialAdapter"
    private var interstitialAd: InterstitialAd? = null

    override fun load() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        if (metaPlacementId == null) {
            val message = "Meta placement ID is null"
            CXLogger.e(TAG, placementName, message)
            listener?.onError(CloudXErrorCode.ADAPTER_INVALID_SERVER_EXTRAS.toCloudXError(message = message))
            return
        }

        // Check if an ad is already loaded
        val existingInterstitialAd = this.interstitialAd
        if (checkAndHandleAdReady(metaPlacementId, existingInterstitialAd)) {
            return
        }

        val interstitialAd = InterstitialAd(contextProvider.getContext(), metaPlacementId)
        this.interstitialAd = interstitialAd

        // Check if the newly constructed ad is already loaded
        if (checkAndHandleAdReady(metaPlacementId, interstitialAd)) {
            return
        }

        CXLogger.d(
            TAG,
            placementName,
            "Loading interstitial ad for Meta placement: $metaPlacementId"
        )
        interstitialAd.loadAd(
            interstitialAd.buildLoadAdConfig()
                .withAdListener(createAdListener(metaPlacementId, listener))
                .withBid(adm)
                .build()
        )
    }

    override fun show() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        CXLogger.d(
            TAG,
            placementName,
            "Attempting to show interstitial ad for Meta placement: $metaPlacementId"
        )
        val ad = this.interstitialAd
        if (ad == null || !ad.isAdLoaded) {
            CXLogger.w(
                TAG,
                placementName,
                "Interstitial ad not ready to show for Meta placement: $metaPlacementId"
            )
            listener?.onError(
                CloudXErrorCode.ADAPTER_INVALID_LOAD_STATE.toCloudXError(
                    message = "Interstitial ad is not loaded"
                )
            )
            return
        }

        // Check if the ad is already expired or invalidated, and do not show ad if that is the case
        if (ad.isAdInvalidated) {
            CXLogger.w(
                TAG,
                placementName,
                "Interstitial ad invalidated for Meta placement: $metaPlacementId"
            )
            listener?.onError(
                CloudXErrorCode.ADAPTER_INVALID_LOAD_STATE.toCloudXError(
                    message = "Interstitial ad is invalidated"
                )
            )
            return
        }

        // Show the ad
        CXLogger.d(
            TAG,
            placementName,
            "Showing interstitial ad for Meta placement: $metaPlacementId"
        )
        ad.show()
    }

    override fun destroy() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        CXLogger.d(
            TAG,
            placementName,
            "Destroying interstitial ad for Meta placement: $metaPlacementId"
        )
        interstitialAd?.destroy()
        interstitialAd = null
        listener = null
    }

    private fun createAdListener(
        metaPlacementId: String,
        listener: CloudXInterstitialAdapterListener?
    ) = object : InterstitialAdListener {
        override fun onError(ad: Ad?, adError: AdError?) {
            CXLogger.e(
                TAG,
                placementName,
                "Interstitial ad failed to load for Meta placement $metaPlacementId with error: ${adError?.errorMessage} (${adError?.errorCode})"
            )
            listener?.onError(adError.toCloudXError())
        }

        override fun onAdLoaded(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Interstitial ad loaded successfully for Meta placement: $metaPlacementId"
            )
            listener?.onLoad()
        }

        override fun onAdClicked(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Interstitial ad clicked for Meta placement: $metaPlacementId"
            )
            listener?.onClick()
        }

        override fun onLoggingImpression(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Interstitial ad impression logged for Meta placement: $metaPlacementId"
            )
            listener?.onImpression()
        }

        override fun onInterstitialDisplayed(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Interstitial ad displayed for Meta placement: $metaPlacementId"
            )
            listener?.onShow()
        }

        override fun onInterstitialDismissed(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Interstitial ad dismissed for Meta placement: $metaPlacementId"
            )
            listener?.onHide()
        }
    }

    /**
     * Helper function to check if an interstitial ad is ready and handle the callback
     * Returns true if ad was ready (and callback was triggered), false otherwise
     */
    private fun checkAndHandleAdReady(placementId: String, ad: InterstitialAd?): Boolean {
        return if (ad != null && ad.isAdLoaded && !ad.isAdInvalidated) {
            CXLogger.d(
                TAG,
                placementName,
                "Interstitial ad already loaded for Meta placement: $placementId"
            )
            listener?.onLoad()
            true
        } else {
            false
        }
    }
}
