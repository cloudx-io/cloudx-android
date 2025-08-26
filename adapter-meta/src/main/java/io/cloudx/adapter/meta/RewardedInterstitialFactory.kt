package io.cloudx.adapter.meta

import android.app.Activity
import io.cloudx.sdk.internal.adapter.*
import io.cloudx.sdk.Result

internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AudienceNetworkAdsVersion) {

    override fun create(
        activity: Activity,
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXRewardedInterstitialAdapterListener,
    ): Result<CloudXRewardedInterstitialAdapter, String> = Result.Success(
        RewardedInterstitialAdapter(
            activity,
            adUnitId = adm,
            listener
        )
    )
}