package io.cloudx.adapter.cloudx

import android.os.Bundle
import io.cloudx.sdk.internal.adapter.CloudXAdapterMetaData
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

internal object InterstitialFactory :
    CloudXInterstitialAdapterFactory,
    CloudXAdapterMetaData by CloudXAdapterMetaData("cloudxdsp-version") {

    override fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXInterstitialAdapterListener,
    ): Result<CloudXInterstitialAdapter, String> = Result.Success(
        StaticBidInterstitial(
            context = contextProvider.getContext(),
            adm = adm,
            listener = listener
        )
    )
}