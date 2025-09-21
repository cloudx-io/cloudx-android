package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.ads.banner.BannerManager
import io.cloudx.sdk.internal.ads.fullscreen.interstitial.InterstitialManager
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.RewardedInterstitialManager
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.initialization.BidAdNetworkFactories
import io.cloudx.sdk.internal.size
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker

internal class AdFactoryImpl(
    private val appKey: String,
    private val config: Config,
    private val factories: BidAdNetworkFactories,
    private val metricsTracker: MetricsTracker,
    private val eventTracker: EventTracker,
    private val winLossTracker: WinLossTracker,
    private val connectionStatusService: ConnectionStatusService,
) : AdFactory {

    private val TAG = "AdFactoryImpl"

    // ===== Banner and Native Ad Creation =====

    // TODO. Refactor.
    //  For now, to speed things up, I'll use this API to create both Banner and Native Ads.
    override fun createBannerManager(params: AdFactory.CreateBannerParams): BannerManager? {
        val placementName = params.placementName
        val adType = params.adType

        // Validating placement exists for the key and ad type passed here.
        val placement = config.placements[placementName]
        if (placement == null || placement.toAdType() != adType) {
            logCantFindPlacement(placementName)
            return null
        }

        val refreshRateMillis = when (placement) {
            is Config.Placement.Banner -> placement.refreshRateMillis
            is Config.Placement.Native -> placement.refreshRateMillis
            else -> 0
        }

        val bidFactories = when (placement) {
            is Config.Placement.MREC -> factories.mrecBanners
            is Config.Placement.Banner -> factories.stdBanners
            is Config.Placement.Native -> factories.nativeAds
            else -> emptyMap()
        }

        val miscParams = BannerFactoryMiscParams(
            enforceCloudXImpressionVerification = true,
            adType = adType,
            adViewSize = adType.size()
        )

        val hasCloseButton = when (placement) {
            is Config.Placement.MREC -> placement.hasCloseButton
            is Config.Placement.Banner -> placement.hasCloseButton
            is Config.Placement.Native -> placement.hasCloseButton
            else -> false
        }

        return BannerManager(
            placementId = placement.id,
            placementName = placement.name,
            adViewContainer = params.adViewAdapterContainer,
            bannerVisibility = params.bannerVisibility,
            refreshSeconds = (refreshRateMillis / 1000),
            adType = adType,
            bidFactories = bidFactories,
            bidRequestExtrasProviders = factories.bidRequestExtrasProviders,
            bidAdLoadTimeoutMillis = placement.adLoadTimeoutMillis.toLong(),
            miscParams = miscParams,
            bidApi = createBidApi(placement.bidResponseTimeoutMillis),
            cdpApi = createCdpApi(),
            eventTracker = eventTracker,
            metricsTracker = metricsTracker,
            winLossTracker = winLossTracker,
            connectionStatusService = connectionStatusService,
            accountId = config.accountId ?: "",
            appKey = appKey
        )
    }

    // ===== Interstitial Ad Creation =====

    override fun createInterstitial(params: AdFactory.CreateAdParams): CloudXInterstitialAd? {
        val placementName = params.placementName
        val placement = config.placements[placementName] as? Config.Placement.Interstitial
        if (placement == null) {
            logCantFindPlacement(placementName)
            return null
        }
        val bidApi = createBidApi(placement.bidResponseTimeoutMillis)

        return InterstitialManager(
            placementId = placement.id,
            placementName = placement.name,
            bidFactories = factories.interstitials,
            bidRequestExtrasProviders = factories.bidRequestExtrasProviders,
            bidAdLoadTimeoutMillis = placement.adLoadTimeoutMillis.toLong(),
            bidApi = bidApi,
            cdpApi = createCdpApi(),
            eventTracker = eventTracker,
            metricsTracker = metricsTracker,
            winLossTracker = winLossTracker,
            connectionStatusService = connectionStatusService,
            accountId = config.accountId ?: "",
            appKey = appKey
        )
    }

    // ===== Rewarded Ad Creation =====

    override fun createRewarded(params: AdFactory.CreateAdParams): CloudXRewardedInterstitialAd? {
        val placementName = params.placementName
        val placement = config.placements[placementName] as? Config.Placement.Rewarded
        if (placement == null) {
            logCantFindPlacement(placementName)
            return null
        }
        val bidApi = createBidApi(placement.bidResponseTimeoutMillis)

        return RewardedInterstitialManager(
            placementId = placement.id,
            placementName = placement.name,
            bidFactories = factories.rewardedInterstitials,
            bidRequestExtrasProviders = factories.bidRequestExtrasProviders,
            bidAdLoadTimeoutMillis = placement.adLoadTimeoutMillis.toLong(),
            bidApi = bidApi,
            cdpApi = createCdpApi(),
            eventTracker = eventTracker,
            metricsTracker = metricsTracker,
            winLossTracker = winLossTracker,
            connectionStatusService = connectionStatusService,
            accountId = config.accountId ?: "",
            appKey = appKey
        )
    }

    // ===== Private Helper Methods =====

    private fun createBidApi(timeoutMillis: Int) = BidApi(
        ResolvedEndpoints.auctionEndpoint,
        timeoutMillis.toLong()
    )

    private fun createCdpApi() = CdpApi(ResolvedEndpoints.cdpEndpoint)

    private fun logCantFindPlacement(placement: String) {
        CXLogger.w(TAG, "can't create $placement placement: missing in SDK Config")
    }
}

private fun Config.Placement.toAdType(): AdType? = when (this) {
    is Config.Placement.MREC -> AdType.Banner.MREC
    is Config.Placement.Banner -> AdType.Banner.Standard
    is Config.Placement.Interstitial -> AdType.Interstitial
    is Config.Placement.Rewarded -> AdType.Rewarded

    is Config.Placement.Native -> when (this.templateType) {
        Config.Placement.Native.TemplateType.Medium -> AdType.Native.Medium
        Config.Placement.Native.TemplateType.Small -> AdType.Native.Small
        is Config.Placement.Native.TemplateType.Unknown -> null
    }
}