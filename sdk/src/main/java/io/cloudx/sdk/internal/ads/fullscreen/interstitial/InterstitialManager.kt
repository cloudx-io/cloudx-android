package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.CloudXAdRevenueListener
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.ads.AdLoader
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdEvent
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdManager
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker

private class InterstitialManagerImpl(
    private val placementName: String,
    private val placementId: String,
    private val adLoader: AdLoader<InterstitialAdapterDelegate>,
) : CloudXInterstitialAd {
    val fullscreenAdManager = FullscreenAdManager(
        tag = "InterstitialManager",
        placementName = placementName,
        placementId = placementId,
        adType = AdType.Interstitial,
        adLoader = adLoader,
        tryHandleCurrentEvent = {
            when (this) {
                InterstitialAdapterDelegateEvent.Show -> FullscreenAdEvent.Show
                is InterstitialAdapterDelegateEvent.Click -> FullscreenAdEvent.Click
                InterstitialAdapterDelegateEvent.Hide -> FullscreenAdEvent.Hide
                else -> null
            }
        }
    )

    override var listener: CloudXInterstitialListener? = null
        set(value) {
            field = value
            fullscreenAdManager.listener = value
        }

    override var revenueListener: CloudXAdRevenueListener? = null
        set(value) {
            field = value
            fullscreenAdManager.revenueListener = value
        }

    override val isAdReady: Boolean
        get() = fullscreenAdManager.isAdReady

    override fun load() = fullscreenAdManager.load()
    override fun show() = fullscreenAdManager.show()
    override fun destroy() = fullscreenAdManager.destroy()
}

internal fun InterstitialManager(
    placementId: String,
    placementName: String,
    bidFactories: Map<AdNetwork, CloudXInterstitialAdapterFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    bidAdLoadTimeoutMillis: Long,
    bidApi: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    winLossTracker: WinLossTracker,
    connectionStatusService: ConnectionStatusService,
    accountId: String,
    appKey: String,
    appId: String
): CloudXInterstitialAd {

    val bidRequestProvider = BidRequestProvider(
        bidRequestExtrasProviders = bidRequestExtrasProviders
    )

    val bidSource =
        BidInterstitialSource(
            factories = bidFactories,
            placementId = placementId,
            placementName = placementName,
            requestBid = bidApi,
            cdpApi = cdpApi,
            generateBidRequest = bidRequestProvider,
            eventTracker = eventTracker,
            metricsTracker = metricsTracker,
            winLossTracker = winLossTracker,
            bidRequestTimeoutMillis = 0,
            accountId = accountId,
            appKey = appKey,
            appId = appId
        )

    val adLoader = AdLoader(
        placementName = placementName,
        placementId = placementId,
        bidAdSource = bidSource,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        connectionStatusService = connectionStatusService,
        winLossTracker = winLossTracker
    )

    return InterstitialManagerImpl(
        placementName = placementName,
        placementId = placementId,
        adLoader = adLoader
    )
}
