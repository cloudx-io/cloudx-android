package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.CloudXFullscreenAd
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
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

internal fun InterstitialManager(
    placementId: String,
    placementName: String,
    cacheSize: Int,
    bidFactories: Map<AdNetwork, CloudXInterstitialAdapterFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    bidApi: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    listener: CloudXInterstitialListener?,
    accountId: String,
    appKey: String
): CloudXInterstitialAd {

    val bidRequestProvider = BidRequestProvider(
        bidRequestExtrasProviders
    )

    val bidSource =
        BidInterstitialSource(
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

    return InterstitialManagerImpl(
        bidAdSource = bidSource,
        bidMaxBackOffTimeMillis = bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        cacheSize = cacheSize,
        connectionStatusService = connectionStatusService,
        appLifecycleService = appLifecycleService,
        listener = listener
    )
}

private class InterstitialManagerImpl(
    bidAdSource: BidAdSource<InterstitialAdapterDelegate>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    cacheSize: Int,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    private val listener: CloudXInterstitialListener?,
) : CloudXInterstitialAd,
    CloudXFullscreenAd by FullscreenAdManager(
        bidAdSource,
        bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis,
        cacheSize,
        AdType.Interstitial,
        connectionStatusService,
        appLifecycleService,
        listener,
        {
            when (this) {
                InterstitialAdapterDelegateEvent.Show -> FullscreenAdEvent.Show
                is InterstitialAdapterDelegateEvent.Click -> FullscreenAdEvent.Click
                InterstitialAdapterDelegateEvent.Hide -> FullscreenAdEvent.Hide
                else -> null
            }
        }
    )
