package io.cloudx.sdk.internal.core.ad.source.bid

import android.app.Activity
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.BidRewardedInterstitialFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.core.ad.source.adapterLoggingDecoration
import io.cloudx.sdk.internal.core.ad.source.baseAdDecoration
import io.cloudx.sdk.internal.core.ad.source.bidAdDecoration
import io.cloudx.sdk.internal.core.ad.source.decorate
import io.cloudx.sdk.internal.core.ad.source.metricsTrackerDecoration
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableRewardedInterstitial
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.tracking.AdEventApi
import io.cloudx.sdk.internal.tracking.MetricsTracker

internal fun BidRewardedInterstitialSource(
    activity: Activity,
    factories: Map<AdNetwork, BidRewardedInterstitialFactory>,
    placementId: String,
    placementName: String,
    requestBid: BidApi,
    cdpApi: CdpApi,
    generateBidRequest: BidRequestProvider,
    adEventApi: AdEventApi,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    metricsTrackerNew: MetricsTrackerNew,
    bidRequestTimeoutMillis: Long,
    accountId: String,
    appKey: String
): BidAdSource<SuspendableRewardedInterstitial> {
    val adType = AdType.Rewarded

    return BidAdSource(
        generateBidRequest,
        BidRequestProvider.Params(
            adId = placementId,
            adType = adType,
            placementName = placementName,
            accountId = accountId,
            appKey = appKey
        ),
        requestBid,
        cdpApi,
        eventTracker,
        metricsTracker,
        metricsTrackerNew
    ) {

        val price = it.price
        val network = it.adNetwork
        val adId = it.adId
        val bidId = it.bidId
        val adm = it.adm
        val nurl = it.nurl
        val params = it.params
        val auctionId = it.auctionId

        SuspendableRewardedInterstitial(
            price,
            network,
            adId
        ) { listener ->
            // TODO. IMPORTANT. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[network]?.create(
                activity,
                adId,
                bidId,
                adm,
                params,
                listener
            ) as Result.Success).value
        }.decorate(
            baseAdDecoration() +
                    metricsTrackerDecoration(placementId, price, metricsTracker) +
                    bidAdDecoration(bidId, auctionId, adEventApi, eventTracker) +
                    adapterLoggingDecoration(
                        adUnitId = adId,
                        adNetwork = network,
                        networkTimeoutMillis = bidRequestTimeoutMillis,
                        type = adType,
                        placementName = placementName,
                        price = price
                    )
        )
    }
}