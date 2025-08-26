package io.cloudx.sdk.internal.adfactory

import io.cloudx.sdk.CloudXAdView
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXRewardedAd
import io.cloudx.sdk.Interstitial
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.RewardedInterstitial
import io.cloudx.sdk.RewardedInterstitialListener
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.Banner
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.resolver.BidAdNetworkFactories
import io.cloudx.sdk.internal.decorate
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal class AdFactoryImpl(
    private val appKey: String,
    private val config: Config,
    private val factories: BidAdNetworkFactories,
    private val metricsTrackerNew: MetricsTrackerNew,
    private val eventTracker: EventTracker,
    private val connectionStatusService: ConnectionStatusService,
    private val appLifecycleService: AppLifecycleService,
    private val activityLifecycleService: ActivityLifecycleService
) : AdFactory {

    private val TAG = "AdFactoryImpl"

    override fun createInterstitial(params: AdFactory.CreateAdParams<CloudXInterstitialListener>): CloudXInterstitialAd? {
        val placementName = params.placementName
        val placement = config.placements[placementName] as? Config.Placement.Interstitial
        if (placement == null) {
            logCantFindPlacement(placementName)
            return null
        }
        val bidApi = createBidApi(placement.bidResponseTimeoutMillis)

        return Interstitial(
            params.activity,
            placementId = placement.id,
            placementName = placement.name,
            cacheSize = config.precacheSize,
            bidFactories = factories.interstitials,
            bidRequestExtrasProviders = factories.bidRequestExtrasProviders,
            bidMaxBackOffTimeMillis = BID_AD_LOAD_BACKOFF_MAX_MILLIS,
            bidAdLoadTimeoutMillis = placement.adLoadTimeoutMillis.toLong(),
            bidApi = bidApi,
            cdpApi = createCdpApi(),
            eventTracker = eventTracker,
            metricsTrackerNew = metricsTrackerNew,
            connectionStatusService = connectionStatusService,
            appLifecycleService = appLifecycleService,
            // TODO. Nullable support.
            listener = params.listener.decorate(),
            accountId = config.accountId ?: "",
            appKey = appKey
        )
    }

    override fun createRewarded(params: AdFactory.CreateAdParams<RewardedInterstitialListener>): CloudXRewardedAd? {
        val placementName = params.placementName
        val placement = config.placements[placementName] as? Config.Placement.Rewarded
        if (placement == null) {
            logCantFindPlacement(placementName)
            return null
        }
        val bidApi = createBidApi(placement.bidResponseTimeoutMillis)

        return RewardedInterstitial(
            params.activity,
            placementId = placement.id,
            placementName = placement.name,
            cacheSize = config.precacheSize,
            bidFactories = factories.rewardedInterstitials,
            bidRequestExtrasProviders = factories.bidRequestExtrasProviders,
            bidMaxBackOffTimeMillis = BID_AD_LOAD_BACKOFF_MAX_MILLIS,
            bidAdLoadTimeoutMillis = placement.adLoadTimeoutMillis.toLong(),
            bidApi = bidApi,
            cdpApi = createCdpApi(),
            eventTracker = eventTracker,
            metricsTrackerNew = metricsTrackerNew,
            connectionStatusService = connectionStatusService,
            appLifecycleService = appLifecycleService,
            // TODO. Nullable support.
            listener = params.listener.decorate(),
            accountId = config.accountId ?: "",
            appKey = appKey
        )
    }

    // TODO. Refactor.
    //  For now, to speed things up, I'll use this API to create both Banner and Native Ads.
    override fun createBanner(params: AdFactory.CreateBannerParams): CloudXAdView? {
        val placementName = params.placementName
        val adType = params.adType

        // Validating placement exists for the key and ad type passed here.
        val placement = config.placements[placementName]
        if (placement == null || placement.toAdType() != adType) {
            logCantFindPlacement(placementName)
            return null
        }

        val size = when (adType) {
            is AdType.Banner -> adType.size
            is AdType.Native -> adType.size
            else -> AdViewSize(0, 0)
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
            adViewSize = size
        )

        val hasCloseButton = when (placement) {
            is Config.Placement.MREC -> placement.hasCloseButton
            is Config.Placement.Banner -> placement.hasCloseButton
            is Config.Placement.Native -> placement.hasCloseButton
            else -> false
        }

        val activity = params.activity

        return CloudXAdView(
            activity,
            suspendPreloadWhenInvisible = true,
            adViewSize = size,
            createBanner = { adViewContainer, bannerVisibility, suspendPreloadWhenInvisible ->
                Banner(
                    activity,
                    placementId = placement.id,
                    placementName = placement.name,
                    adViewContainer,
                    bannerVisibility,
                    refreshSeconds = (refreshRateMillis / 1000),
                    adType = adType,
                    preloadTimeMillis = 5000L,
                    bidFactories = bidFactories,
                    bidRequestExtrasProviders = factories.bidRequestExtrasProviders,
                    bidMaxBackOffTimeMillis = BID_AD_LOAD_BACKOFF_MAX_MILLIS,
                    bidAdLoadTimeoutMillis = placement.adLoadTimeoutMillis.toLong(),
                    miscParams = miscParams,
                    bidApi = createBidApi(placement.bidResponseTimeoutMillis),
                    cdpApi = createCdpApi(),
                    eventTracker = eventTracker,
                    metricsTrackerNew = metricsTrackerNew,
                    connectionStatusService = connectionStatusService,
                    activityLifecycleService = activityLifecycleService,
                    appLifecycleService = appLifecycleService,
                    config.accountId ?: "",
                    appKey = appKey
                )
            },
            hasCloseButton = hasCloseButton,
            placementName = placementName
        ).apply {
            listener = params.listener
        }
    }

    private fun createBidApi(timeoutMillis: Int) = BidApi(
        ResolvedEndpoints.auctionEndpoint,
        timeoutMillis.toLong()
    )

    private fun createCdpApi() = CdpApi(
        endpointUrl = ResolvedEndpoints.cdpEndpoint,
        10_000
    )

    private fun logCantFindPlacement(placement: String) {
        Logger.w(TAG, "can't create $placement placement: missing in SDK Config")
    }
}

private const val BID_AD_LOAD_BACKOFF_MAX_MILLIS = 20_000L

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