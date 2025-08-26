package io.cloudx.adapter.meta

import android.app.Activity
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData

@Keep
internal object BannerFactory : CloudXAdViewAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {
    // Consider suspend?
    override fun create(
        activity: Activity,
        adViewContainer: CloudXAdViewAdapterContainer,
        refreshSeconds: Int?,
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        miscParams: BannerFactoryMiscParams,
        listener: CloudXAdViewAdapterListener,
    ): Result<CloudXAdViewAdapter, String> = Result.Success(
        MetaBannerAdapter(
            activity,
            adViewContainer,
            adUnitId = adm,
            miscParams.adViewSize,
            listener,
        )
    )

    override val sizeSupport: List<AdViewSize>
        get() = listOf(AdViewSize.Standard, AdViewSize.MREC)
}

internal class MetaBannerAdapter(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val adUnitId: String,
    private val adViewSize: AdViewSize,
    private var listener: CloudXAdViewAdapterListener?
) : CloudXAdViewAdapter {

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
            adViewContainer.onAdd(this)
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
            adViewContainer.onRemove(it)
        }
        adView = null

        listener = null
    }

    private fun createAdListener(listener: CloudXAdViewAdapterListener?) = object : AdListener {

        override fun onError(ad: Ad?, adError: AdError?) {
            log(
                TAG,
                "Banner ad failed to load for placement $adUnitId with error: ${adError?.errorMessage} (${adError?.errorCode})"
            )
            listener?.onError(
                CloudXAdapterError(
                    description = adError?.errorMessage ?: "Unknown error"
                )
            )
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