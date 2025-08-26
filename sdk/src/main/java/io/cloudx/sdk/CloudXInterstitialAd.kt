package io.cloudx.sdk

import android.app.Activity
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.*
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.source.bid.*
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableInterstitial
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableInterstitialEvent
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

// TODO. Refactor. This should do for now.
interface CloudXInterstitialAd : BaseFullscreenAd

interface InterstitialListener : CloudXAdListener

internal fun Interstitial(
    activity: Activity,
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
    metricsTrackerNew: MetricsTrackerNew,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    listener: InterstitialListener,
    accountId: String,
    appKey: String
): CloudXInterstitialAd {

    val bidRequestProvider = BidRequestProvider(
        activity,
        bidRequestExtrasProviders
    )

    val bidSource =
        BidInterstitialSource(
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

    return InterstitialImpl(
        bidAdSource = bidSource,
        bidMaxBackOffTimeMillis = bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        cacheSize = cacheSize,
        connectionStatusService = connectionStatusService,
        appLifecycleService = appLifecycleService,
        listener = listener
    )
}

private class InterstitialImpl(
    bidAdSource: BidAdSource<SuspendableInterstitial>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    cacheSize: Int,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    private val listener: InterstitialListener,
) : CloudXInterstitialAd,
    BaseFullscreenAd by BaseFullscreenAdImpl(
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
                SuspendableInterstitialEvent.Show -> BaseSuspendableFullscreenAdEvent.Show
                is SuspendableInterstitialEvent.Click -> BaseSuspendableFullscreenAdEvent.Click
                SuspendableInterstitialEvent.Hide -> BaseSuspendableFullscreenAdEvent.Hide
                else -> null
            }
        }
    )