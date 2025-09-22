package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.createAdEventTrackingDecorator
import io.cloudx.sdk.internal.ads.createAdapterEventLoggingDecorator
import io.cloudx.sdk.internal.ads.decorate
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
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
        provideBidRequest = generateBidRequest,
        bidRequestParams = BidRequestProvider.Params(
            placementId = placementId,
            adType = adType,
            placementName,
            accountId = accountId,
            appKey = appKey
        ),
        requestBid = requestBid,
        cdpApi = cdpApi,
        eventTracker = eventTracker,
        metricsTracker = metricsTracker
    ) { createBidAdParams ->
        val placementName = createBidAdParams.placementName
        val placementId = createBidAdParams.placementId
        val adNetwork = createBidAdParams.adNetwork
        val price = createBidAdParams.price
        val bid = createBidAdParams.bid
        val bidId = bid.id
        val adm = bid.adm
        val params = bid.adapterExtras
        val auctionId = createBidAdParams.auctionId

        InterstitialAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = adNetwork,
            externalPlacementId = null,
            price = price,
        ) { listener ->
            // TODO. IMPORTANT. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[adNetwork]?.create(
                contextProvider = ContextProvider(),
                placementName = placementName,
                placementId = placementId,
                bidId = bidId,
                adm = adm,
                serverExtras = params,
                listener = listener
            ) as Result.Success).value
        }.decorate(
            adEventDecorator = createAdEventTrackingDecorator(
                bid = bid,
                auctionId = auctionId,
                eventTracker = eventTracker,
                winLossTracker = winLossTracker
            ) + createAdapterEventLoggingDecorator(
                placementName = placementName,
                adNetwork = adNetwork,
                type = adType,
            )
        )
    }
}
