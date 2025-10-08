package io.cloudx.sdk.internal.initialization

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdViewSizeSupport
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.toAdapterPackagePrefix

internal class AdapterFactoryResolverImpl: AdapterFactoryResolver {

    override fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories {
        val initializers = mutableMapOf<AdNetwork, CloudXAdapterInitializer>()
        val bidRequestExtrasProviders = mutableMapOf<AdNetwork, CloudXAdapterBidRequestExtrasProvider>()
        val interstitials = mutableMapOf<AdNetwork, CloudXInterstitialAdapterFactory>()
        val rewardedInterstitials = mutableMapOf<AdNetwork, CloudXRewardedInterstitialAdapterFactory>()
        val banners = mutableMapOf<AdNetwork, CloudXAdViewAdapterFactory>()
        val nativeAds = mutableMapOf<AdNetwork, CloudXAdViewAdapterFactory>()

        for (network in forTheseNetworks) {
            val prefix = network.toAdapterPackagePrefix()

            (instance("${prefix}Initializer") as? CloudXAdapterInitializer)?.let {
                initializers[network] = it
            }

            (instance("${prefix}BidRequestExtrasProvider") as? CloudXAdapterBidRequestExtrasProvider)?.let {
                bidRequestExtrasProviders[network] = it
            }

            (instance("${prefix}InterstitialFactory") as? CloudXInterstitialAdapterFactory)?.let {
                interstitials[network] = it
            }

            (instance("${prefix}RewardedInterstitialFactory") as? CloudXRewardedInterstitialAdapterFactory)?.let {
                rewardedInterstitials[network] = it
            }

            (instance("${prefix}BannerFactory") as? CloudXAdViewAdapterFactory)?.let {
                banners[network] = it
            }

            (instance("${prefix}NativeAdFactory") as? CloudXAdViewAdapterFactory)?.let {
                nativeAds[network] = it
            }
        }

        val stdBanners = mutableMapOf<AdNetwork, CloudXAdViewAdapterFactory>()
        val mrecBanners = mutableMapOf<AdNetwork, CloudXAdViewAdapterFactory>()
        populateBannersByBannerSize(banners, stdBanners, mrecBanners)

        return BidAdNetworkFactories(
            initializers,
            bidRequestExtrasProviders,
            interstitials,
            rewardedInterstitials,
            stdBanners,
            mrecBanners,
            nativeAds
        )
    }
}

private fun <B: CloudXAdViewSizeSupport, N> populateBannersByBannerSize(
    allBannerFactories: Map<N, B>,
    stdBanners: MutableMap<N, B>,
    mrecBanners: MutableMap<N, B>,
) {
    allBannerFactories.onEach {
        if (it.value.sizeSupport.contains(AdViewSize.Standard)) {
            stdBanners[it.key] = it.value
        }
        if (it.value.sizeSupport.contains(AdViewSize.MREC)) {
            mrecBanners[it.key] = it.value
        }
    }
}

private fun instance(className: String) = try {
    Class.forName(className).kotlin.objectInstance
} catch (e: Exception) {
    CXLogger.e("AdapterFactoryResolver", "Failed to load adapter class: $className", e)
    null
}