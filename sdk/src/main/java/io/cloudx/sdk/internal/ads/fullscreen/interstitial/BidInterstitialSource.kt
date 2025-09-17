package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.adapterLoggingDecoration
import io.cloudx.sdk.internal.ads.baseAdDecoration
import io.cloudx.sdk.internal.ads.bidAdDecoration
import io.cloudx.sdk.internal.ads.decorate
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.imp_tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result

internal fun BidInterstitialSource(
    factories: Map<AdNetwork, CloudXInterstitialAdapterFactory>,
    placementId: String,
    placementName: String,
    requestBid: BidApi,
    cdpApi: CdpApi,
    generateBidRequest: BidRequestProvider,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    winLossTracker: WinLossTracker,
    bidRequestTimeoutMillis: Long,
    accountId: String,
    appKey: String
): BidAdSource<InterstitialAdapterDelegate> {
    val adType = AdType.Interstitial

    return BidAdSource(
        generateBidRequest,
        BidRequestProvider.Params(
            placementId = placementId,
            adType = adType,
            placementName,
            accountId = accountId,
            appKey = appKey
        ),
        requestBid,
        cdpApi,
        eventTracker,
        metricsTracker,
        winLossTracker
    ) {

        val placementName = it.placementName
        val placementId = it.placementId
        val adNetwork = it.adNetwork
        val price = it.price
        val bid = it.bid
        val bidId = bid.id
        val adm = bid.adm
        val params = bid.adapterExtras
        val auctionId = it.auctionId

        InterstitialAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = adNetwork,
            externalPlacementId = null,
            price = price,
        ) { listener ->
            // TODO. IMPORTANT. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[adNetwork]?.create(
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
                        adNetwork = adNetwork,
                        networkTimeoutMillis = bidRequestTimeoutMillis,
                        type = adType,
                        placementName = placementName,
                        price = price
                    )
        )
    }
}
