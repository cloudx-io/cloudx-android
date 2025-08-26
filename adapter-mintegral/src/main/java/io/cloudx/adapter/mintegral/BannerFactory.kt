package io.cloudx.adapter.mintegral

import android.app.Activity
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.BidBannerFactory
import io.cloudx.sdk.internal.adapter.MetaData

internal object BannerFactory : BidBannerFactory,
    MetaData by MetaData(MintegralVersion) {
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

        BannerAdapter(
            activity,
            adViewContainer,
            placementId = params?.placementId(),
            adUnitId = adm,
            bidId = params?.bidId(),
            miscParams.adViewSize,
            listener,
        )
    )

    override val sizeSupport: List<AdViewSize>
        get() = listOf(AdViewSize.Standard, AdViewSize.MREC)
}