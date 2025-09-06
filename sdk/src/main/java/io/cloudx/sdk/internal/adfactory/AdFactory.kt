package io.cloudx.sdk.internal.adfactory

import android.app.Activity
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudXAdView
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXRewardedAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.RewardedInterstitialListener
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.resolver.BidAdNetworkFactories
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal interface AdFactory {
    // Banner and Native Ad creation
    // TODO. Refactor.
    //  For now, to speed things up, I'll use this API to create both Banner and Native Ads.
    fun createBanner(params: CreateBannerParams): CloudXAdView?

    // Interstitial Ad creation
    fun createInterstitial(params: CreateAdParams<CloudXInterstitialListener>): CloudXInterstitialAd?

    // Rewarded Ad creation
    fun createRewarded(params: CreateAdParams<RewardedInterstitialListener>): CloudXRewardedAd?

    open class CreateAdParams<T>(
        val placementName: String,
        val listener: T?
    )

    class CreateBannerParams(
        val adType: AdType,
        val activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?,
    ) : CreateAdParams<CloudXAdViewListener>(
        placementName, listener
    )
}

internal fun AdFactory(
    appKey: String,
    config: Config,
    factories: BidAdNetworkFactories,
    metricsTrackerNew: MetricsTrackerNew,
    eventTracker: EventTracker,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    activityLifecycleService: ActivityLifecycleService
): AdFactory =
    AdFactoryImpl(
        appKey,
        config,
        factories,
        metricsTrackerNew,
        eventTracker,
        connectionStatusService,
        appLifecycleService,
        activityLifecycleService
    )