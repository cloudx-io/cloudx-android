package io.cloudx.adapter.meta

import android.app.Activity
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener

@Keep
internal object InterstitialFactory :
    CloudXInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {

    override fun create(
        activity: Activity,
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXInterstitialAdapterListener,
    ): Result<CloudXInterstitialAdapter, String> = Result.Success(
        MetaInterstitialAdapter(
            activity,
            adUnitId = adm,
            listener
        )
    )
}

internal class MetaInterstitialAdapter(
    private val activity: Activity,
    private val adUnitId: String,
    private var listener: CloudXInterstitialAdapterListener?
) : CloudXInterstitialAdapter, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private var interstitialAd: InterstitialAd? = null

    override fun load() {
        log(TAG, "Loading interstitial ad for placement: $adUnitId")

        // Check if an ad is already loaded
        val existingInterstitialAd = this.interstitialAd
        if (checkAndHandleAdReady(existingInterstitialAd)) {
            return
        }

        val interstitialAd = InterstitialAd(activity, adUnitId)
        this.interstitialAd = interstitialAd

        // Check if the newly constructed ad is already loaded
        if (checkAndHandleAdReady(interstitialAd)) {
            return
        }

        log(TAG, "Starting to load interstitial ad for placement: $adUnitId")
        interstitialAd.loadAd(
            interstitialAd.buildLoadAdConfig()
                .withAdListener(createAdListener(listener))
                .build()
        )
    }

    override fun show() {
        log(TAG, "Attempting to show interstitial ad for placement: $adUnitId")
        val ad = this.interstitialAd
        if (ad == null || !ad.isAdLoaded) {
            log(TAG, "Interstitial ad not ready to show for placement: $adUnitId (ad not loaded)")
            listener?.onError(CloudXAdapterError(description = "Interstitial ad is not loaded"))
            return
        }

        // Check if ad is already expired or invalidated, and do not show ad if that is the case. You will not get paid to show an invalidated ad.
        if (ad.isAdInvalidated) {
            log(TAG, "Interstitial ad invalidated for placement: $adUnitId")
            listener?.onError(CloudXAdapterError(description = "Interstitial ad is invalidated"))
            return
        }

        // Show the ad
        log(TAG, "Showing interstitial ad for placement: $adUnitId")
        ad.show()
    }

    override fun destroy() {
        log(TAG, "Destroying interstitial ad for placement: $adUnitId")
        interstitialAd?.destroy()
        interstitialAd = null
        listener = null
    }

    private fun createAdListener(listener: CloudXInterstitialAdapterListener?) =
        object : InterstitialAdListener {
            override fun onError(ad: Ad?, adError: AdError?) {
                log(
                    TAG,
                    "Interstitial ad failed to load for placement $adUnitId with error: ${adError?.errorMessage} (${adError?.errorCode})"
                )
                listener?.onError(
                    CloudXAdapterError(
                        description = adError?.errorMessage ?: "Unknown error"
                    )
                )
            }

            override fun onAdLoaded(ad: Ad?) {
                log(TAG, "Interstitial ad loaded successfully for placement: $adUnitId")
                listener?.onLoad()
            }

            override fun onAdClicked(ad: Ad?) {
                log(TAG, "Interstitial ad clicked for placement: $adUnitId")
                listener?.onClick()
            }

            override fun onLoggingImpression(ad: Ad?) {
                log(TAG, "Interstitial ad impression logged for placement: $adUnitId")
                listener?.onImpression()
            }

            override fun onInterstitialDisplayed(ad: Ad?) {
                log(TAG, "Interstitial ad displayed for placement: $adUnitId")
                listener?.onShow()
            }

            override fun onInterstitialDismissed(ad: Ad?) {
                log(TAG, "Interstitial ad dismissed for placement: $adUnitId")
                listener?.onHide()
            }
        }

    /**
     * Helper function to check if an interstitial ad is ready and handle the callback
     * Returns true if ad was ready (and callback was triggered), false otherwise
     */
    private fun checkAndHandleAdReady(ad: InterstitialAd?): Boolean {
        return if (ad != null && ad.isAdLoaded && !ad.isAdInvalidated) {
            log(TAG, "Interstitial ad already loaded for placement: $adUnitId")
            listener?.onLoad()
            true
        } else {
            false
        }
    }
}

private const val TAG = "MetaInterstitialAdapter"