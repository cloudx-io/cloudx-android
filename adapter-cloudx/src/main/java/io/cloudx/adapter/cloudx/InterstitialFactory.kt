package io.cloudx.adapter.cloudx

import android.app.Activity
import io.cloudx.sdk.internal.adapter.*
import io.cloudx.sdk.Result

internal object InterstitialFactory :
    BidInterstitialFactory,
    MetaData by MetaData("cloudxdsp-version") {

    override fun create(
        activity: Activity,
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXInterstitialAdapterListener,
    ): Result<CloudXInterstitialAdapter, String> = Result.Success(
        StaticBidInterstitial(
            activity,
            adm,
            listener
        )
    )
}