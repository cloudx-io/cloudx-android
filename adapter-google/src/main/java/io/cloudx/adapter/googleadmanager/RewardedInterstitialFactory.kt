package io.cloudx.adapter.googleadmanager

import android.app.Activity
import io.cloudx.sdk.internal.adapter.*
import io.cloudx.sdk.Result

internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(AdManagerVersion) {

    override fun create(
        activity: Activity,
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXRewardedInterstitialListener
    ): Result<CloudXRewardedInterstitial, String> = Result.Success(
        RewardedInterstitialAdapter(
            activity,
            adUnitId = adm,
            listener
        )
    )
}