package io.cloudx.sdk

import android.app.Activity
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.BidRequestExtrasProvider
import io.cloudx.sdk.internal.adapter.BidRewardedInterstitialFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSource
import io.cloudx.sdk.internal.core.ad.source.bid.BidRewardedInterstitialSource
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableRewardedInterstitial
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableRewardedInterstitialEvent
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

// TODO. Refactor. This should do for now.
interface CloudXRewardedAd : BaseFullscreenAd

interface RewardedInterstitialListener : BasePublisherListener {

    /**
     * User was rewarded.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onUserRewarded(cloudXAd: CloudXAd)
}

internal fun RewardedInterstitial(
    activity: Activity,
    placementId: String,
    placementName: String,
    cacheSize: Int,
    bidFactories: Map<AdNetwork, BidRewardedInterstitialFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, BidRequestExtrasProvider>,
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
        activity,
        bidRequestExtrasProviders
    )

    val bidSource =
        BidRewardedInterstitialSource(
            activity,
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
    bidAdSource: BidAdSource<SuspendableRewardedInterstitial>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    cacheSize: Int,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    private val listener: RewardedInterstitialListener,
) : CloudXRewardedAd,
    BaseFullscreenAd by BaseFullscreenAdImpl(
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
                SuspendableRewardedInterstitialEvent.Show -> BaseSuspendableFullscreenAdEvent.Show
                is SuspendableRewardedInterstitialEvent.Click -> BaseSuspendableFullscreenAdEvent.Click
                SuspendableRewardedInterstitialEvent.Hide -> BaseSuspendableFullscreenAdEvent.Hide
                SuspendableRewardedInterstitialEvent.Reward -> {
                    listener.onUserRewarded(cloudXAd)
                    null
                }

                else -> null
            }
        }
    )