package io.cloudx.adapter.googleadmanager

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import io.cloudx.sdk.internal.adapter.AdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdError
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener

internal class InterstitialAdapter(
    private val activity: Activity,
    private val adUnitId: String,
    private var listener: CloudXInterstitialAdapterListener?
) : CloudXInterstitialAdapter, AdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private var ad: AdManagerInterstitialAd? = null

    override fun load() {
        AdManagerInterstitialAd.load(
            activity,
            adUnitId,
            AdManagerAdRequest.Builder().build(),
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(p0: LoadAdError) {
                    super.onAdFailedToLoad(p0)
                    listener?.onError(CloudXAdError(description = p0.toString()))
                }

                override fun onAdLoaded(p0: AdManagerInterstitialAd) {
                    super.onAdLoaded(p0)

                    ad = p0
                    listener?.onLoad()
                }
            })
    }

    override fun show() {
        val ad = this.ad
        if (ad == null) {
            listener?.onError(CloudXAdError(description = "can't show: ad is not loaded"))
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                listener?.onError(CloudXAdError(description = adError.message))
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

        ad.show(activity)
    }

    override fun destroy() {
        ad = null
        listener = null
    }
}