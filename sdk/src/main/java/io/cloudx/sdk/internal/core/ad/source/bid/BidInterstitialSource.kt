package io.cloudx.sdk.internal.core.ad.source.bid

import android.app.Activity
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.core.ad.source.adapterLoggingDecoration
import io.cloudx.sdk.internal.core.ad.source.baseAdDecoration
import io.cloudx.sdk.internal.core.ad.source.bidAdDecoration
import io.cloudx.sdk.internal.core.ad.source.decorate
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableInterstitial
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal fun BidInterstitialSource(
    activity: Activity,
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
): BidAdSource<SuspendableInterstitial> {
    val adType = AdType.Interstitial

    return BidAdSource(
        generateBidRequest,
        BidRequestProvider.Params(
            adId = placementId,
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

        val price = it.price
        val adNetwork = it.adNetwork
        val adId = it.adId
        val bidId = it.bidId
        val adm = it.adm
        val nurl = it.nurl
        val params = it.params
        val auctionId = it.auctionId

        SuspendableInterstitial(
            price,
            adNetwork,
            adId,
        ) { listener ->
            // TODO. IMPORTANT. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[adNetwork]?.create(
                activity,
                adId,
                bidId,
                adm,
                params,
                listener
            ) as Result.Success).value
        }.decorate(
            baseAdDecoration() +
                    bidAdDecoration(bidId, auctionId, eventTracker) +
                    adapterLoggingDecoration(
                        adUnitId = adId,
                        adNetwork = adNetwork,
                        networkTimeoutMillis = bidRequestTimeoutMillis,
                        type = adType,
                        placementName = placementName,
                        price = price
                    )
        )
    }
}
