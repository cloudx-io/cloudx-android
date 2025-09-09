package io.cloudx.adapter.cloudx

import android.app.Activity
import android.os.Bundle
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.adapter.*
import io.cloudx.sdk.Result

internal object BannerFactory : CloudXAdViewAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData("cloudx-version") {
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
    ): Result<CloudXAdViewAdapter, String> {

        val banner = StaticBidBanner(
            activity, adViewContainer, adm, listener
        )

        return banner.let { Result.Success(it) }
    }

    override val sizeSupport: List<AdViewSize>
        get() = listOf( AdViewSize.Standard, AdViewSize.MREC)
}