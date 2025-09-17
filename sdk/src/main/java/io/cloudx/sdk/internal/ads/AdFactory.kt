package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.ads.banner.BannerManager
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.initialization.BidAdNetworkFactories
import kotlinx.coroutines.flow.StateFlow

internal interface AdFactory {
    // Banner and Native Ad creation
    // TODO. Refactor.
    //  For now, to speed things up, I'll use this API to create both Banner and Native Ads.
    fun createBannerManager(params: CreateBannerParams): BannerManager?

    // Interstitial Ad creation
    fun createInterstitial(params: CreateAdParams): CloudXInterstitialAd?

    // Rewarded Ad creation
    fun createRewarded(params: CreateAdParams): CloudXRewardedInterstitialAd?

    open class CreateAdParams(val placementName: String)

    class CreateBannerParams(
        val adType: AdType,
        val adViewAdapterContainer: CloudXAdViewAdapterContainer,
        val bannerVisibility: StateFlow<Boolean>,
        placementName: String,
    ) : CreateAdParams(placementName)
}

internal fun AdFactory(
    appKey: String,
    config: Config,
    factories: BidAdNetworkFactories,
    metricsTracker: MetricsTracker,
    eventTracker: EventTracker,
    connectionStatusService: ConnectionStatusService,
): AdFactory =
    AdFactoryImpl(
        appKey = appKey,
        config = config,
        factories = factories,
        metricsTracker = metricsTracker,
        eventTracker = eventTracker,
        connectionStatusService = connectionStatusService,
    )