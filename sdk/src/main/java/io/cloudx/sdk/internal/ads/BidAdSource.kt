package io.cloudx.sdk.internal.ads

import android.os.Bundle
import com.xor.XorEncryption
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.PlacementLoopIndexTracker
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.bid.BidResponse
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.EventType
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.util.Result
import java.util.UUID
import kotlin.system.measureTimeMillis

internal interface BidAdSource<T : Destroyable> {

    /**
     * @return the bid or null if no bid
     */
    suspend fun requestBid(): Result<BidAdSourceResponse<T>, CloudXError>
}

internal open class BidAdSourceResponse<T : Destroyable>(
    val bidItemsByRank: List<Item<T>>
) {

    class Item<T>(
        val id: String,
        val adNetwork: AdNetwork,
        val adNetworkOriginal: AdNetwork, // todo: only used for demo
        val price: Double,
        val priceRaw: String,
        val rank: Int,
        val lurl: String?,
        val createBidAd: suspend () -> T,
    )
}

internal fun <T : Destroyable> BidAdSource(
    provideBidRequest: BidRequestProvider,
    bidRequestParams: BidRequestProvider.Params,
    requestBid: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTracker: MetricsTracker,
    createBidAd: suspend (CreateBidAdParams) -> T,
): BidAdSource<T> =
    BidAdSourceImpl(
        provideBidRequest,
        bidRequestParams,
        requestBid,
        cdpApi,
        eventTracker,
        metricsTracker,
        createBidAd
    )

internal class CreateBidAdParams(
    val placementName: String,
    val placementId: String,
    val bidId: String,
    val adm: String,
    val adapterExtras: Bundle,
    val burl: String?,
    val nurl: String?,
    val adNetwork: AdNetwork,
    val price: Double,
    val auctionId: String
)

private class BidAdSourceImpl<T : Destroyable>(
    private val provideBidRequest: BidRequestProvider,
    private val bidRequestParams: BidRequestProvider.Params,
    private val requestBid: BidApi,
    private val cdpApi: CdpApi,
    private val eventTracking: EventTracker,
    private val metricsTracker: MetricsTracker,
    private val createBidAd: suspend (CreateBidAdParams) -> T,
) : BidAdSource<T> {

    private val logTag = "BidAdSourceImpl"

    override suspend fun requestBid(): Result<BidAdSourceResponse<T>, CloudXError> {
        val auctionId = UUID.randomUUID().toString()
        val bidRequestParamsJson = provideBidRequest.invoke(bidRequestParams, auctionId)

        val currentLoopIndex = PlacementLoopIndexTracker.getCount(bidRequestParams.placementName)

        CXLogger.d(logTag, "")
        CXLogger.d(logTag, "======== loop-index=$currentLoopIndex")
        CXLogger.d(logTag, "")
// User Params
        val userParams = SdkKeyValueState.userKeyValues
        CXLogger.d(logTag, "user params: $userParams")

        val appParams = SdkKeyValueState.appKeyValues
        CXLogger.d(logTag, "app params: $appParams")

        val isCdpDisabled = ResolvedEndpoints.cdpEndpoint.isBlank()

        val enrichedPayload = if (isCdpDisabled) {
            CXLogger.d(logTag, "Skipping enrichment.")
            bidRequestParamsJson
        } else {
            CXLogger.d(logTag, "Making a call to CDP")
            when (val enrichResult = cdpApi.enrich(bidRequestParamsJson)) {
                is Result.Success -> {
                    CXLogger.d(logTag, "Received enriched data from CDP")
                    enrichResult.value
                }

                is Result.Failure -> {
                    CXLogger.e(
                        logTag,
                        "CDP enrichment failed: ${enrichResult.value.effectiveMessage}. Using original payload."
                    )
                    bidRequestParamsJson
                }
            }
        }

        CXLogger.d(
            logTag,
            "Sending BidRequest [loop-index=$currentLoopIndex] for placementId: ${bidRequestParams.placementId}"
        )

        val result: Result<BidResponse, CloudXError>
        val bidRequestLatencyMillis = measureTimeMillis {
            result = requestBid.invoke(bidRequestParams.appKey, enrichedPayload)
        }

        metricsTracker.trackNetworkCall(MetricsType.Network.BidRequest, bidRequestLatencyMillis)

        TrackingFieldResolver.setRequestData(
            auctionId,
            bidRequestParamsJson
        )
        TrackingFieldResolver.setLoopIndex(
            auctionId,
            PlacementLoopIndexTracker.getCount(bidRequestParams.placementName)
        )

        val payload = TrackingFieldResolver.buildPayload(auctionId)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracking.send(impressionId, campaignId, "1", EventType.BID_REQUEST)
        }

        return when (result) {
            is Result.Success -> {
                val resp = result.value.toBidAdSourceResponse(bidRequestParams, createBidAd)
                Result.Success(resp)
            }

            is Result.Failure -> {
                Result.Failure(result.value)
            }
        }
    }
}

private fun <T : Destroyable> BidResponse.toBidAdSourceResponse(
    bidRequestParams: BidRequestProvider.Params,
    createBidAd: suspend (CreateBidAdParams) -> T,
): BidAdSourceResponse<T> {

    val items = seatBid.asSequence()
        .flatMap { it.bid }
        .map { bid ->

            val price = (bid.price ?: 0.0).toDouble()
            val priceRaw = bid.priceRaw ?: "0.0"
            val adNetworkOriginal = bid.adNetwork
            val adNetwork = when (adNetworkOriginal) {
                AdNetwork.CloudXSecond -> AdNetwork.CloudX
                else -> adNetworkOriginal
            }

            BidAdSourceResponse.Item(
                id = bid.id,
                adNetwork = adNetwork,
                adNetworkOriginal = adNetworkOriginal,
                price = price,
                priceRaw = priceRaw,
                rank = bid.rank,
                lurl = bid.lurl,
                createBidAd = {
                    createBidAd(
                        CreateBidAdParams(
                            placementName = bidRequestParams.placementName,
                            placementId = bidRequestParams.placementId,
                            bidId = bid.id,
                            adm = bid.adm,
                            adapterExtras = bid.adapterExtras,
                            burl = bid.burl,
                            nurl = bid.nurl,
                            adNetwork = adNetwork,
                            price = price,
                            auctionId = bid.auctionId
                        )
                    )
                }
            )
        }.sortedBy {
            it.rank
        }.toList()

    return BidAdSourceResponse(items)
}