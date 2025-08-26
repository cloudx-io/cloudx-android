package io.cloudx.sdk.internal.core.resolver

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.BannerSizeSupport
import io.cloudx.sdk.internal.adapter.BidBannerFactory
import io.cloudx.sdk.internal.adapter.BidInterstitialFactory
import io.cloudx.sdk.internal.adapter.BidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.BidRewardedInterstitialFactory

internal class AdapterFactoryResolverImpl: AdapterFactoryResolver {

    override fun resolveBidAdNetworkFactories(forTheseNetworks: Set<AdNetwork>): BidAdNetworkFactories {
        val initializers = mutableMapOf<AdNetwork, CloudXAdapterInitializer>()
        val bidRequestExtrasProviders = mutableMapOf<AdNetwork, BidRequestExtrasProvider>()
        val interstitials = mutableMapOf<AdNetwork, BidInterstitialFactory>()
        val rewardedInterstitials = mutableMapOf<AdNetwork, BidRewardedInterstitialFactory>()
        val banners = mutableMapOf<AdNetwork, BidBannerFactory>()
        val nativeAds = mutableMapOf<AdNetwork, BidBannerFactory>()

        for (network in forTheseNetworks) {
            val prefix = network.toAdapterPackagePrefix()

            (instance("${prefix}Initializer") as? CloudXAdapterInitializer)?.let {
                initializers[network] = it
            }

            (instance("${prefix}BidRequestExtrasProvider") as? BidRequestExtrasProvider)?.let {
                bidRequestExtrasProviders[network] = it
            }

            (instance("${prefix}InterstitialFactory") as? BidInterstitialFactory)?.let {
                interstitials[network] = it
            }

            (instance("${prefix}RewardedInterstitialFactory") as? BidRewardedInterstitialFactory)?.let {
                rewardedInterstitials[network] = it
            }

            (instance("${prefix}BannerFactory") as? BidBannerFactory)?.let {
                banners[network] = it
            }

            (instance("${prefix}NativeAdFactory") as? BidBannerFactory)?.let {
                nativeAds[network] = it
            }
        }

        val stdBanners = mutableMapOf<AdNetwork, BidBannerFactory>()
        val mrecBanners = mutableMapOf<AdNetwork, BidBannerFactory>()
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

private fun <B: BannerSizeSupport, N> populateBannersByBannerSize(
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
    Logger.e(TAG, e.toString())
    null
}

private fun AdNetwork.toAdapterPackagePrefix(): String? = when (this) {
    AdNetwork.GoogleAdManager -> "googleadmanager"
    AdNetwork.TestNetwork -> "testbidnetwork"
    AdNetwork.Meta -> "meta"
    AdNetwork.Mintegral -> "mintegral"
    AdNetwork.CloudX -> "cloudx"
    AdNetwork.CloudXSecond -> "cloudx"
    is AdNetwork.Unknown -> null
}?.let {
    "io.cloudx.adapter.$it."
}

private const val TAG = "AdapterFactoryResolverImpl"