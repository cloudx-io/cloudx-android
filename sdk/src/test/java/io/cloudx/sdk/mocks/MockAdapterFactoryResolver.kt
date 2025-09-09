package io.cloudx.sdk.mocks

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.initialization.AdapterFactoryResolver
import io.cloudx.sdk.internal.initialization.BidAdNetworkFactories
import io.mockk.mockk

internal class MockAdapterFactoryResolver : AdapterFactoryResolver {

    override fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories {
        return BidAdNetworkFactories(
            initializers = mutableMapOf(
                AdNetwork.CloudX to MockAdapterInitializer()
            ),
            bidRequestExtrasProviders = mockk(),
            interstitials = mockk(),
            rewardedInterstitials = mockk(),
            stdBanners = mockk(),
            mrecBanners = mockk(),
            nativeAds = mockk()
        )
    }
}