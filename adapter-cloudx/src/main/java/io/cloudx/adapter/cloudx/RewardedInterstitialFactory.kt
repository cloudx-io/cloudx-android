package io.cloudx.adapter.cloudx

import android.app.Activity
import io.cloudx.sdk.internal.adapter.*
import io.cloudx.sdk.Result

internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData("cloudx-version") {

    override fun create(
        activity: Activity,
        placementId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXRewardedInterstitialAdapterListener
    ): Result<CloudXRewardedInterstitialAdapter, String> = Result.Success(
        StaticBidRewardedInterstitial(
            activity,
            adm,
            listener
        )
    )
}