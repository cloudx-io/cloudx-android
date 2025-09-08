package io.cloudx.adapter.mintegral

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider

internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData(MintegralVersion) {

    override fun create(
        contextProvider: ContextProvider,
        placementId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXRewardedInterstitialAdapterListener
    ): Result<CloudXRewardedInterstitialAdapter, String> = Result.Success(
        RewardedInterstitialAdapter(
            contextProvider,
            placementId = params?.placementId(),
            adUnitId = adm,
            bidId = params?.bidId(),
            listener
        )
    )
}