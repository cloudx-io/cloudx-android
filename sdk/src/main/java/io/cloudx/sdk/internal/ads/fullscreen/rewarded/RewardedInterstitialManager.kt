package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.CloudXRewardedInterstitialAd
import io.cloudx.sdk.CloudXRewardedInterstitialListener
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.ads.AdLoader
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdEvent
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdManager
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.imp_tracker.win_loss.WinLossTracker

private class RewardedInterstitialManager(
    private val adLoader: AdLoader<RewardedInterstitialAdapterDelegate>,
) : CloudXRewardedInterstitialAd {
    val fullscreenAdManager = FullscreenAdManager(
        adLoader = adLoader,
        placementType = AdType.Rewarded,
        tryHandleCurrentEvent = { cloudXAd ->
            when (this) {
                RewardedInterstitialAdapterDelegateEvent.Show -> FullscreenAdEvent.Show
                is RewardedInterstitialAdapterDelegateEvent.Click -> FullscreenAdEvent.Click
                RewardedInterstitialAdapterDelegateEvent.Hide -> FullscreenAdEvent.Hide
                RewardedInterstitialAdapterDelegateEvent.Reward -> {
                    listener?.onUserRewarded(cloudXAd)
                    null
                }

                else -> null
            }
        }
    )
    override var listener: CloudXRewardedInterstitialListener? = null
        set(value) {
            field = value
            fullscreenAdManager.listener = value
        }

    override val isAdLoaded: Boolean
        get() = fullscreenAdManager.isAdLoaded

    override fun load() = fullscreenAdManager.load()
    override fun show() = fullscreenAdManager.show()
    override fun destroy() = fullscreenAdManager.destroy()
}

internal fun RewardedInterstitialManager(
    placementId: String,
    placementName: String,
    bidFactories: Map<AdNetwork, CloudXRewardedInterstitialAdapterFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    bidAdLoadTimeoutMillis: Long,
    bidApi: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    winLossTracker: WinLossTracker,
    connectionStatusService: ConnectionStatusService,
    accountId: String,
    appKey: String
): CloudXRewardedInterstitialAd {

    val bidRequestProvider = BidRequestProvider(
        bidRequestExtrasProviders = bidRequestExtrasProviders
    )

    val bidSource =
        BidRewardedInterstitialSource(
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
            appKey = appKey
        )

    val adLoader = AdLoader(
        placementName = placementName,
        placementId = placementId,
        bidAdSource = bidSource,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        connectionStatusService = connectionStatusService
    )

    return RewardedInterstitialManager(
        adLoader = adLoader,
    )
}
