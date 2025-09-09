package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.ads.adapterLoggingDecoration
import io.cloudx.sdk.internal.ads.baseAdDecoration
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.bidAdDecoration
import io.cloudx.sdk.internal.ads.decorate
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal fun BidRewardedInterstitialSource(
    factories: Map<AdNetwork, CloudXRewardedInterstitialAdapterFactory>,
    placementId: String,
    placementName: String,
    requestBid: BidApi,
    cdpApi: CdpApi,
    generateBidRequest: BidRequestProvider,
    eventTracker: EventTracker,
    metricsTrackerNew: MetricsTrackerNew,
    bidRequestTimeoutMillis: Long,
    accountId: String,
    appKey: String
): BidAdSource<RewardedInterstitialAdapterDelegate> {
    val adType = AdType.Rewarded

    return BidAdSource(
        generateBidRequest,
        BidRequestProvider.Params(
            placementId = placementId,
            adType = adType,
            placementName = placementName,
            accountId = accountId,
            appKey = appKey
        ),
        requestBid,
        cdpApi,
        eventTracker,
        metricsTrackerNew
    ) {

        val placementName = it.placementName
        val placementId = it.placementId
        val network = it.adNetwork
        val price = it.price
        val bidId = it.bidId
        val adm = it.adm
        val nurl = it.nurl
        val params = it.adapterExtras
        val auctionId = it.auctionId

        RewardedInterstitialAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = network,
            externalPlacementId = null,
            price = price
        ) { listener ->
            // TODO. IMPORTANT. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[network]?.create(
                ContextProvider(),
                placementId,
                bidId,
                adm,
                params,
                listener
            ) as Result.Success).value
        }.decorate(
            baseAdDecoration() +
                    bidAdDecoration(bidId, auctionId, eventTracker) +
                    adapterLoggingDecoration(
                        placementId = placementId,
                        adNetwork = network,
                        networkTimeoutMillis = bidRequestTimeoutMillis,
                        type = adType,
                        placementName = placementName,
                        price = price
                    )
        )
    }
}