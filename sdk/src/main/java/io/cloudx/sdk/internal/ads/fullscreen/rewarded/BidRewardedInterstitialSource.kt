package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
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

internal fun BidRewardedInterstitialSource(
    factories: Map<AdNetwork, CloudXRewardedInterstitialAdapterFactory>,
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
    appKey: String,
    appId: String
): BidAdSource<RewardedInterstitialAdapterDelegate> {
    val adType = AdType.Rewarded
    return BidAdSource(
        provideBidRequest = generateBidRequest,
        bidRequestParams = BidRequestProvider.Params(
            placementId = placementId,
            adType = adType,
            placementName = placementName,
            accountId = accountId,
            appKey = appKey,
            appId = appId
        ),
        requestBid = requestBid,
        cdpApi = cdpApi,
        eventTracker = eventTracker,
        metricsTracker = metricsTracker,
        winLossTracker = winLossTracker
    ) {

        val placementName = it.placementName
        val placementId = it.placementId
        val network = it.adNetwork
        val price = it.price
        val bid = it.bid
        val bidId = bid.id
        val adm = bid.adm
        val params = bid.adapterExtras
        val auctionId = it.auctionId

        RewardedInterstitialAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = network,
            externalPlacementId = null,
            price = price,
        ) { listener ->
            (factories[network]?.create(
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
                winLossTracker = winLossTracker,
                type = adType
            ) + createAdapterEventLoggingDecorator(
                placementName = placementName,
                adNetwork = network,
                type = adType,
            )
        )
    }
}
