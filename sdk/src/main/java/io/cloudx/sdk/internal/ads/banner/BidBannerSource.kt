package io.cloudx.sdk.internal.ads.banner

import android.app.Activity
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.ads.adapterLoggingDecoration
import io.cloudx.sdk.internal.ads.baseAdDecoration
import io.cloudx.sdk.internal.ads.bidAdDecoration
import io.cloudx.sdk.internal.ads.decorate
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew

internal fun BidBannerSource(
    activity: Activity,
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
    metricsTrackerNew: MetricsTrackerNew,
    miscParams: BannerFactoryMiscParams,
    bidRequestTimeoutMillis: Long,
    accountId: String,
    appKey: String
): BidAdSource<BannerAdapterDelegate> =
    BidAdSource(
        generateBidRequest,
        BidRequestProvider.Params(
            placementId = placementId,
            adType = placementType,
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
        val lurl = it.lurl
        val params = it.adapterExtras
        val auctionId = it.auctionId

        BannerAdapterDelegate(
            placementName = placementName,
            placementId = placementId,
            adNetwork = network,
            externalPlacementId = null,
            price = price,
            nurl = nurl,
            lurl = lurl
        ) { listener ->
            // TODO. Explicit Result cast isn't "cool", even though there's try catch somewhere.
            (factories[network]?.create(
                activity, adViewContainer, refreshSeconds, placementId, bidId,
                adm, params, miscParams, listener
            ) as Result.Success).value
        }.decorate(
            baseAdDecoration() +
                    bidAdDecoration(bidId, auctionId, eventTracker) +
                    adapterLoggingDecoration(
                        placementId = placementId,
                        adNetwork = network,
                        networkTimeoutMillis = bidRequestTimeoutMillis,
                        type = placementType,
                        placementName = placementName,
                        price = price
                    )
        )
    }