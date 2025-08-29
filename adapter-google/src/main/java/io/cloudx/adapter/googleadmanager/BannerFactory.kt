package io.cloudx.adapter.googleadmanager

import android.app.Activity
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData

internal object BannerFactory : CloudXAdViewAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AdManagerVersion) {
    // Consider suspend?
    override fun create(
        activity: Activity,
        adViewContainer: CloudXAdViewAdapterContainer,
        refreshSeconds: Int?,
        placementId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        miscParams: BannerFactoryMiscParams,
        listener: CloudXAdViewAdapterListener,
    ): Result<CloudXAdViewAdapter, String> = Result.Success(
        BannerAdapter(
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