package io.cloudx.adapter.cloudx

import android.app.Activity
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
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
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