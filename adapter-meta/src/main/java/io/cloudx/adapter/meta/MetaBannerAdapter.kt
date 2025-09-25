package io.cloudx.adapter.meta

import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.toCloudXError

@Keep
internal object BannerFactory : CloudXAdViewAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {
    // Consider suspend?
    override fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        adViewContainer: CloudXAdViewAdapterContainer,
        refreshSeconds: Int?,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        miscParams: BannerFactoryMiscParams,
        listener: CloudXAdViewAdapterListener,
    ): Result<CloudXAdViewAdapter, String> = Result.Success(
        MetaBannerAdapter(
            contextProvider = contextProvider,
            placementName = placementName,
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
    private val contextProvider: ContextProvider,
    private val placementName: String,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val serverExtras: Bundle,
    private val adm: String,
    private val adViewSize: AdViewSize,
    private var listener: CloudXAdViewAdapterListener?
) : CloudXAdViewAdapter {

    private val TAG = "MetaBannerAdapter"
    private var adView: AdView? = null

    override fun load() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        if (metaPlacementId.isNullOrEmpty()) {
            val message = "Meta placement ID is null"
            CXLogger.e(TAG, placementName, message)
            listener?.onError(CloudXErrorCode.ADAPTER_INVALID_SERVER_EXTRAS.toCloudXError(message = message))
            return
        }

        CXLogger.d(TAG, placementName, "Loading banner ad for Meta placement: $metaPlacementId")
        val adView = AdView(
            contextProvider.getContext(),
            metaPlacementId,
            if (adViewSize == AdViewSize.Standard) AdSize.BANNER_HEIGHT_50 else AdSize.RECTANGLE_HEIGHT_250
        )
        this.adView = adView

        with(adView) {
            adViewContainer.onAdd(this)
            CXLogger.d(
                TAG,
                placementName,
                "Starting to load banner ad for Meta placement: $metaPlacementId"
            )

            loadAd(
                buildLoadAdConfig()
                    .withAdListener(createAdListener(metaPlacementId, listener))
                    .withBid(adm)
                    .build()
            )
        }
    }

    override fun destroy() {
        val metaPlacementId = serverExtras.getMetaPlacementId()
        CXLogger.d(TAG, placementName, "Destroying banner ad for Meta placement: $metaPlacementId")
        adView?.let {
            it.destroy()
            adViewContainer.onRemove(it)
        }
        adView = null
        listener = null
    }

    private fun createAdListener(
        metaPlacementId: String,
        listener: CloudXAdViewAdapterListener?
    ) = object : AdListener {
        override fun onError(ad: Ad?, adError: AdError?) {
            CXLogger.e(
                TAG,
                placementName,
                "Banner ad error for Meta placement $metaPlacementId with error: ${adError?.errorMessage} (${adError?.errorCode})"
            )
            listener?.onError(adError.toCloudXError())
        }

        override fun onAdLoaded(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Banner ad loaded for Meta placement: $metaPlacementId"
            )
            listener?.onLoad()
        }

        override fun onAdClicked(ad: Ad?) {
            CXLogger.d(TAG, placementName, "Banner ad clicked for Meta placement: $metaPlacementId")
            listener?.onClick()
        }

        override fun onLoggingImpression(ad: Ad?) {
            CXLogger.d(
                TAG,
                placementName,
                "Banner ad impression logged for Meta placement: $metaPlacementId"
            )
            listener?.onShow()
            listener?.onImpression()
        }
    }
}
