package io.cloudx.adapter.cloudx

import android.os.Bundle
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

internal object NativeAdFactory : CloudXAdViewAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData("cloudx-version") {
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
        NativeAd(
            contextProvider = contextProvider,
            adViewContainer = adViewContainer,
            adm = adm,
            adType = miscParams.adType as AdType.Native,
            listener = listener,
        )
    )

    override val sizeSupport: List<AdViewSize>
        get() = listOf(AdType.Native.Small.size, AdType.Native.Medium.size)
}