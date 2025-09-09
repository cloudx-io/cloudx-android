package io.cloudx.sdk.internal.adapter

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.AdViewSize

interface CloudXAdViewAdapterFactory : CloudXAdapterMetaData, CloudXAdViewSizeSupport {

    fun create(
        activity: Activity,
        adViewContainer: CloudXAdViewAdapterContainer,
        refreshSeconds: Int?,
        placementId: String,
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