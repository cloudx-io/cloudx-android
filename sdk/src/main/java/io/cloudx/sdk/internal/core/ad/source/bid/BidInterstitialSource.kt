package io.cloudx.sdk.internal.core.ad.source.bid

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.core.ad.adapter_delegate.InterstitialAdapterDelegate
import io.cloudx.sdk.internal.core.ad.source.adapterLoggingDecoration
import io.cloudx.sdk.internal.core.ad.source.baseAdDecoration
import io.cloudx.sdk.internal.core.ad.source.bidAdDecoration
import io.cloudx.sdk.internal.core.ad.source.decorate
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal fun BidInterstitialSource(
    factories: Map<AdNetwork, CloudXInterstitialAdapterFactory>,
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
        metricsTrackerNew
    ) {

        val placementName = it.placementName
        val placementId = it.placementId
        val adNetwork = it.adNetwork
        val price = it.price
        val bidId = it.bidId
        val adm = it.adm
        val nurl = it.nurl
        val params = it.params
        val auctionId = it.auctionId

        InterstitialAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = adNetwork,
            externalPlacementId = null,
            price = price
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
