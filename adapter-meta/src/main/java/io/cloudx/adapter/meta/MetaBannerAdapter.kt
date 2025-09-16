package io.cloudx.adapter.meta

import android.app.Activity
import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.util.Result

@Keep
internal object BannerFactory : CloudXAdViewAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {
    // Consider suspend?
    override fun create(
        activity: Activity,
        adViewContainer: CloudXAdViewAdapterContainer,
        refreshSeconds: Int?,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        miscParams: BannerFactoryMiscParams,
        listener: CloudXAdViewAdapterListener,
    ): Result<CloudXAdViewAdapter, String> = Result.Success(
        MetaBannerAdapter(
            activity = activity,
            adViewContainer = adViewContainer,
            serverExtras = serverExtras,
            adm = adm,
            adViewSize = miscParams.adViewSize,
            listener = listener,
        )
    )

    override val sizeSupport: List<AdViewSize>
        get() = listOf(AdViewSize.Standard, AdViewSize.MREC)
}

internal class MetaBannerAdapter(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val serverExtras: Bundle,
    private val adm: String,
    private val adViewSize: AdViewSize,
    private var listener: CloudXAdViewAdapterListener?
) : CloudXAdViewAdapter {

    private val TAG = "MetaBannerAdapter"
    private var adView: AdView? = null

    override fun load() {
        val placementId = serverExtras.getPlacementId()
        if (placementId == null) {
            CXLogger.e(TAG, "Placement ID is null")
            listener?.onError(CloudXAdapterError(description = "Placement ID is null"))
            return
        }

        CXLogger.d(TAG, "Loading banner ad for placement: $placementId")
        val adView = AdView(
            activity,
            placementId,
            if (adViewSize == AdViewSize.Standard) AdSize.BANNER_HEIGHT_50 else AdSize.RECTANGLE_HEIGHT_250
        )
        this.adView = adView

        with(adView) {
            adViewContainer.onAdd(this)
            CXLogger.d(TAG, "Starting to load banner ad for placement: $placementId")

            loadAd(
                buildLoadAdConfig()
                    .withAdListener(createAdListener(placementId, listener))
                    .withBid(adm)
                    .build()
            )
        }
    }

    override fun destroy() {
        CXLogger.d(TAG, "Destroying banner ad for placement: ${serverExtras.getPlacementId()}")
        adView?.let {
            it.destroy()
            adViewContainer.onRemove(it)
        }
        adView = null
        listener = null
    }

    private fun createAdListener(
        placementId: String,
        listener: CloudXAdViewAdapterListener?
    ) = object : AdListener {

        override fun onError(ad: Ad?, adError: AdError?) {
            CXLogger.e(
                TAG,
                "Banner ad failed to load for placement $placementId with error: ${adError?.errorMessage} (${adError?.errorCode})"
            )
            listener?.onError(
                CloudXAdapterError(
                    description = adError?.errorMessage ?: "Unknown error"
                )
            )
        }

        override fun onAdLoaded(ad: Ad?) {
            CXLogger.d(TAG, "Banner ad loaded successfully for placement: $placementId")
            listener?.onLoad()
        }

        override fun onAdClicked(ad: Ad?) {
            CXLogger.d(TAG, "Banner ad clicked for placement: $placementId")
            listener?.onClick()
        }

        override fun onLoggingImpression(ad: Ad?) {
            CXLogger.d(TAG, "Banner ad impression logged for placement: $placementId")
            listener?.onShow()
            listener?.onImpression()
        }
    }
}
