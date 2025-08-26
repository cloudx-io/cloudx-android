package io.cloudx.sdk.mocks

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.core.resolver.AdapterFactoryResolver
import io.cloudx.sdk.internal.core.resolver.BidAdNetworkFactories
import io.mockk.mockk

internal class MockAdapterFactoryResolver : AdapterFactoryResolver {

    override fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories {
        return BidAdNetworkFactories(
            initializers = mutableMapOf(
                AdNetwork.TestNetwork to MockAdapterInitializer()
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