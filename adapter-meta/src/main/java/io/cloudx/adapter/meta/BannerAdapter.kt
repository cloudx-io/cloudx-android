package io.cloudx.adapter.meta

import android.app.Activity
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.adapter.Banner
import io.cloudx.sdk.internal.adapter.BannerContainer
import io.cloudx.sdk.internal.adapter.BannerListener
import io.cloudx.sdk.internal.adapter.CloudXAdError

internal class BannerAdapter(
    private val activity: Activity,
    private val container: BannerContainer,
    private val adUnitId: String,
    private val adViewSize: AdViewSize,
    private var listener: BannerListener?
) : Banner {

    private var adView: AdView? = null

    override fun load() {
        log(TAG, "Loading banner ad for placement: $adUnitId")
        val adView = AdView(
            activity,
            adUnitId,
            if (adViewSize == AdViewSize.Standard) AdSize.BANNER_HEIGHT_50 else AdSize.RECTANGLE_HEIGHT_250
        )
        this.adView = adView

        with(adView) {
            container.onAdd(this)
            log(TAG, "Starting to load banner ad for placement: $adUnitId")

            loadAd(
                buildLoadAdConfig()
                    .withAdListener(createAdListener(listener))
                    .build()
            )
        }
    }

    override fun destroy() {
        log(TAG, "Destroying banner ad for placement: $adUnitId")
        adView?.let {
            it.destroy()
            container.onRemove(it)
        }
        adView = null

        listener = null
    }

    private fun createAdListener(listener: BannerListener?) = object : AdListener {

        override fun onError(ad: Ad?, adError: AdError?) {
            log(
                TAG,
                "Banner ad failed to load for placement $adUnitId with error: ${adError?.errorMessage} (${adError?.errorCode})"
            )
            listener?.onError(CloudXAdError(description = adError?.errorMessage ?: "Unknown error"))
        }

        override fun onAdLoaded(ad: Ad?) {
            log(TAG, "Banner ad loaded successfully for placement: $adUnitId")
            listener?.onLoad()
        }

        override fun onAdClicked(ad: Ad?) {
            log(TAG, "Banner ad clicked for placement: $adUnitId")
            listener?.onClick()
        }

        override fun onLoggingImpression(ad: Ad?) {
            log(TAG, "Banner ad impression logged for placement: $adUnitId")
            listener?.onShow()
            listener?.onImpression()
        }
    }
}

private const val TAG = "MetaBannerAdapter"