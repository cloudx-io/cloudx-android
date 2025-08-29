package io.cloudx.sdk.internal.core.ad.source.bid

import android.app.Activity
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.core.ad.source.adapterLoggingDecoration
import io.cloudx.sdk.internal.core.ad.source.baseAdDecoration
import io.cloudx.sdk.internal.core.ad.source.bidAdDecoration
import io.cloudx.sdk.internal.core.ad.source.decorate
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableRewardedInterstitial
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal fun BidRewardedInterstitialSource(
    activity: Activity,
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
): BidAdSource<SuspendableRewardedInterstitial> {
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

        val price = it.price
        val network = it.adNetwork
        val placementId = it.placementId
        val bidId = it.bidId
        val adm = it.adm
        val nurl = it.nurl
        val params = it.params
        val auctionId = it.auctionId

        SuspendableRewardedInterstitial(
            price,
            network,
            placementId
        ) { listener ->
            // TODO. IMPORTANT. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[network]?.create(
                activity,
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