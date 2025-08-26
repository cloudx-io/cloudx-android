package io.cloudx.sdk.internal.core.resolver

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.AdapterInitializer
import io.cloudx.sdk.internal.adapter.BidBannerFactory
import io.cloudx.sdk.internal.adapter.BidInterstitialFactory
import io.cloudx.sdk.internal.adapter.BidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.BidRewardedInterstitialFactory

internal interface AdapterFactoryResolver {

    fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories
}

internal class BidAdNetworkFactories(
    val initializers: Map<AdNetwork, AdapterInitializer>,
    val bidRequestExtrasProviders: Map<AdNetwork, BidRequestExtrasProvider>,
    val interstitials: Map<AdNetwork, BidInterstitialFactory>,
    val rewardedInterstitials: Map<AdNetwork, BidRewardedInterstitialFactory>,
    val stdBanners: Map<AdNetwork, BidBannerFactory>,
    val mrecBanners: Map<AdNetwork, BidBannerFactory>,
    val nativeAds: Map<AdNetwork, BidBannerFactory>
)

internal fun AdapterFactoryResolver(): AdapterFactoryResolver = LazySingleInstance

private val LazySingleInstance by lazy {
    AdapterFactoryResolverImpl()
}