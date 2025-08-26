package io.cloudx.sdk.internal.core.resolver

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory

internal interface AdapterFactoryResolver {

    fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories
}

internal class BidAdNetworkFactories(
    val initializers: Map<AdNetwork, CloudXAdapterInitializer>,
    val bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    val interstitials: Map<AdNetwork, CloudXInterstitialAdapterFactory>,
    val rewardedInterstitials: Map<AdNetwork, CloudXRewardedInterstitialAdapterFactory>,
    val stdBanners: Map<AdNetwork, CloudXAdViewAdapterFactory>,
    val mrecBanners: Map<AdNetwork, CloudXAdViewAdapterFactory>,
    val nativeAds: Map<AdNetwork, CloudXAdViewAdapterFactory>
)

internal fun AdapterFactoryResolver(): AdapterFactoryResolver = LazySingleInstance

private val LazySingleInstance by lazy {
    AdapterFactoryResolverImpl()
}