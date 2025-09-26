package io.cloudx.sdk.fake

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.initialization.AdapterFactoryResolver
import io.cloudx.sdk.internal.initialization.BidAdNetworkFactories

internal class FakeAdapterFactoryResolver : AdapterFactoryResolver {
    override fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories {
        return BidAdNetworkFactories(
            initializers = mutableMapOf(
                AdNetwork.CloudX to FakeAdapterInitializer()
            ),
            bidRequestExtrasProviders = emptyMap(),
            interstitials = emptyMap(),
            rewardedInterstitials = emptyMap(),
            stdBanners = emptyMap(),
            mrecBanners = emptyMap(),
            nativeAds = emptyMap()
        )
    }
}