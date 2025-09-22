package io.cloudx.adapter.cloudx

import android.os.Bundle
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

internal object RewardedInterstitialFactory :
    CloudXRewardedInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData("cloudx-version") {

    override fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXRewardedInterstitialAdapterListener
    ): Result<CloudXRewardedInterstitialAdapter, String> = Result.Success(
        StaticBidRewardedInterstitial(
            context = contextProvider.getContext(),
            adm = adm,
            placementName = placementName,
            listener = listener
        )
    )
}