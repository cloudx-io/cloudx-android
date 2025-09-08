package io.cloudx.adapter.testbidnetwork

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider

internal object InterstitialFactory :
    CloudXInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData("test-bid-network-version") {

    override fun create(
        contextProvider: ContextProvider,
        placementId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXInterstitialAdapterListener,
    ): Result<CloudXInterstitialAdapter, String> = Result.Success(
        StaticBidInterstitial(
            contextProvider.getContext(),
            adm,
            listener
        )
    )
}