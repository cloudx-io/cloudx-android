package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.adapterLoggingDecoration
import io.cloudx.sdk.internal.ads.baseAdDecoration
import io.cloudx.sdk.internal.ads.bidAdDecoration
import io.cloudx.sdk.internal.ads.decorate
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result

internal fun BidBannerSource(
    adViewContainer: CloudXAdViewAdapterContainer,
    refreshSeconds: Int?,
    factories: Map<AdNetwork, CloudXAdViewAdapterFactory>,
    placementId: String,
    placementName: String,
    placementType: AdType,
    requestBid: BidApi,
    cdpApi: CdpApi,
    generateBidRequest: BidRequestProvider,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    winLossTracker: WinLossTracker,
    miscParams: BannerFactoryMiscParams,
    bidRequestTimeoutMillis: Long,
    accountId: String,
    appKey: String
): BidAdSource<BannerAdapterDelegate> =
    BidAdSource(
        provideBidRequest = generateBidRequest,
        bidRequestParams = BidRequestProvider.Params(
            placementId = placementId,
            adType = placementType,
            placementName = placementName,
            accountId = accountId,
            appKey = appKey
        ),
        requestBid = requestBid,
        cdpApi = cdpApi,
        eventTracker = eventTracker,
        metricsTracker = metricsTracker
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

        BannerAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = network,
            externalPlacementId = null,
            price = price,
        ) { listener ->
            // TODO. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[network]?.create(
                contextProvider = ContextProvider(),
                placementName = placementName,
                placementId = placementId,
                adViewContainer = adViewContainer,
                refreshSeconds = refreshSeconds,
                bidId = bidId,
                adm = adm,
                serverExtras = params,
                miscParams = miscParams,
                listener = listener
            ) as Result.Success).value
        }.decorate(
            baseAdDecoration() +
                    bidAdDecoration(
                        bid = bid,
                        auctionId = auctionId,
                        eventTracker = eventTracker,
                        winLossTracker = winLossTracker
                    ) +
                    adapterLoggingDecoration(
                        placementName = placementName,
                        placementId = placementId,
                        adNetwork = network,
                        networkTimeoutMillis = bidRequestTimeoutMillis,
                        type = placementType,
                        price = price
                    )
        )
    }
