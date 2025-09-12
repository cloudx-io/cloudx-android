package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.CloudXFullscreenAd
import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialListener
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdEvent
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdManager
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker

internal fun RewardedInterstitialManager(
    placementId: String,
    placementName: String,
    cacheSize: Int,
    bidFactories: Map<AdNetwork, CloudXRewardedInterstitialAdapterFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    bidApi: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    listener: CloudXRewardedInterstitialListener,
    accountId: String,
    appKey: String
): CloudXRewardedInterstitialAd {

    val bidRequestProvider = BidRequestProvider(
        bidRequestExtrasProviders
    )

    val bidSource =
        BidRewardedInterstitialSource(
            bidFactories,
            placementId,
            placementName,
            bidApi,
            cdpApi,
            bidRequestProvider,
            eventTracker,
            metricsTracker,
            0,
            accountId,
            appKey
        )

    return RewardedInterstitialManager(
        bidAdSource = bidSource,
        bidMaxBackOffTimeMillis = bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        cacheSize = cacheSize,
        connectionStatusService = connectionStatusService,
        appLifecycleService = appLifecycleService,
        listener = listener
    )
}

private class RewardedInterstitialManager(
    bidAdSource: BidAdSource<RewardedInterstitialAdapterDelegate>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    cacheSize: Int,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    private val listener: CloudXRewardedInterstitialListener,
) : CloudXRewardedInterstitialAd,
    CloudXFullscreenAd by FullscreenAdManager(
        bidAdSource,
        bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis,
        cacheSize,
        AdType.Rewarded,
        connectionStatusService,
        appLifecycleService,
        listener,
        { cloudXAd ->
            when (this) {
                RewardedInterstitialAdapterDelegateEvent.Show -> FullscreenAdEvent.Show
                is RewardedInterstitialAdapterDelegateEvent.Click -> FullscreenAdEvent.Click
                RewardedInterstitialAdapterDelegateEvent.Hide -> FullscreenAdEvent.Hide
                RewardedInterstitialAdapterDelegateEvent.Reward -> {
                    listener.onUserRewarded(cloudXAd)
                    null
                }

                else -> null
            }
        }
    )
