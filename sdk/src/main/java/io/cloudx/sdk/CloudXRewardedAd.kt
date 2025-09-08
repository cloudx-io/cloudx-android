package io.cloudx.sdk

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.adapter_delegate.RewardedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.core.ad.adapter_delegate.RewardedInterstitialAdapterDelegateEvent
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSource
import io.cloudx.sdk.internal.core.ad.source.bid.BidRewardedInterstitialSource
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

// TODO. Refactor. This should do for now.
interface CloudXRewardedAd : CloudXFullscreenAd

interface RewardedInterstitialListener : CloudXAdListener {

    /**
     * User was rewarded.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onUserRewarded(cloudXAd: CloudXAd)
}

internal fun RewardedInterstitial(
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
    metricsTrackerNew: MetricsTrackerNew,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    listener: RewardedInterstitialListener,
    accountId: String,
    appKey: String
): CloudXRewardedAd {

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
            metricsTrackerNew,
            0,
            accountId,
            appKey
        )

    return RewardedInterstitialImpl(
        bidAdSource = bidSource,
        bidMaxBackOffTimeMillis = bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        cacheSize = cacheSize,
        connectionStatusService = connectionStatusService,
        appLifecycleService = appLifecycleService,
        listener = listener
    )
}

private class RewardedInterstitialImpl(
    bidAdSource: BidAdSource<RewardedInterstitialAdapterDelegate>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    cacheSize: Int,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    private val listener: RewardedInterstitialListener,
) : CloudXRewardedAd,
    CloudXFullscreenAd by FullscreenAdManagerImpl(
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