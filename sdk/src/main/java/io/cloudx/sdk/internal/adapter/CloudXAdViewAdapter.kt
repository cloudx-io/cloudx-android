package io.cloudx.sdk.internal.adapter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

interface CloudXAdViewAdapterFactory : CloudXAdapterMetaData, CloudXAdViewSizeSupport {

    fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        adViewContainer: CloudXAdViewAdapterContainer,
        refreshSeconds: Int?,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        miscParams: BannerFactoryMiscParams,
        listener: CloudXAdViewAdapterListener
    ): Result<CloudXAdViewAdapter, String>
}

interface CloudXAdViewAdapter : Destroyable {

    fun load()
}

interface CloudXAdViewAdapterListener : CloudXAdapterErrorListener {

    fun onLoad()
    fun onShow()
    fun onImpression()
    fun onClick()
}

interface CloudXAdViewAdapterContainer {

    fun onAdd(bannerView: View)
    fun onRemove(bannerView: View)
    fun acquireBannerContainer(): ViewGroup
    fun releaseBannerContainer(bannerContainer: ViewGroup)
}

interface CloudXAdViewSizeSupport {

    val sizeSupport: List<AdViewSize>
}

class BannerFactoryMiscParams(
    val enforceCloudXImpressionVerification: Boolean,
    val adType: AdType,
    val adViewSize: AdViewSize
)