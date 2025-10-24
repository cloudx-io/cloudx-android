package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXDestroyable
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.bid.BidResponse
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.EventType
import io.cloudx.sdk.internal.tracker.PlacementLoopIndexTracker
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver.SDK_PARAM_RESPONSE_IN_MILLIS
import io.cloudx.sdk.internal.tracker.XorEncryption
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsType
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result
import java.util.UUID
import kotlin.system.measureTimeMillis

internal class CreateBidAdParams(
    val placementName: String,
    val placementId: String,
    val bid: Bid,
    val adNetwork: AdNetwork,
    val price: Double,
    val auctionId: String
)

internal class BidAdSource<T : CloudXDestroyable>(
    private val provideBidRequest: BidRequestProvider,
    private val bidRequestParams: BidRequestProvider.Params,
    private val requestBid: BidApi,
    private val cdpApi: CdpApi,
    private val eventTracker: EventTracker,
    private val metricsTracker: MetricsTracker,
    private val winLossTracker: WinLossTracker,
    private val createBidAd: suspend (CreateBidAdParams) -> T,
) {

    private val logger = CXLogger.forComponent("BidAdSource")

    suspend fun requestBid(): Result<BidAdSourceResponse<T>, CloudXError> {
        val auctionId = UUID.randomUUID().toString()
        val bidRequestParamsJson = provideBidRequest.invoke(bidRequestParams, auctionId)

        val currentLoopIndex = PlacementLoopIndexTracker.getCount(bidRequestParams.placementName)

        logger.d("")
        logger.d("======== loop-index=$currentLoopIndex")
        logger.d("")
// User Params
        val userParams = SdkKeyValueState.userKeyValues
        logger.d("user params: $userParams")

        val appParams = SdkKeyValueState.appKeyValues
        logger.d("app params: $appParams")

        val isCdpDisabled = ResolvedEndpoints.cdpEndpoint.isBlank()

        val enrichedPayload = if (isCdpDisabled) {
            logger.d("Skipping enrichment.")
            bidRequestParamsJson
        } else {
            logger.d("Making a call to CDP")
            when (val enrichResult = cdpApi.enrich(bidRequestParamsJson)) {
                is Result.Success -> {
                    logger.d("Received enriched data from CDP")
                    enrichResult.value
                }

                is Result.Failure -> {
                    logger.e("CDP enrichment failed: ${enrichResult.value.effectiveMessage}. Using original payload.")
                    bidRequestParamsJson
                }
            }
        }

        logger.d("Sending BidRequest [loop-index=$currentLoopIndex] for placementId: ${bidRequestParams.placementId}")

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
        TrackingFieldResolver.setSdkParam(
            auctionId,
            SDK_PARAM_RESPONSE_IN_MILLIS,
            bidRequestLatencyMillis.toString()
        )

        val payload = TrackingFieldResolver.buildPayload(auctionId)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracker.send(impressionId, campaignId, "1", EventType.BID_REQUEST)
        }

        return when (result) {
            is Result.Success -> {
                val resp = result.value.toBidAdSourceResponse(bidRequestParams, createBidAd, auctionId)

                if (resp.bidItemsByRank.isEmpty()) {
                    logger.d("NO_BID")
                } else {
                    val bidDetails =
                        resp.bidItemsByRank.joinToString(separator = ",\n") {
                            val bid = it.bid
                            val cpm = bid.priceRaw ?: "0.0"
                            "\"bidder\": \"${it.adNetworkOriginal.networkName}\", cpm: $cpm, rank: ${bid.rank}"
                        }
                    logger.d("Bid Success — received ${resp.bidItemsByRank.size} bid(s): [$bidDetails]")
                }

                Result.Success(resp)
            }

            is Result.Failure -> {
                Result.Failure(result.value)
            }
        }
    }
}

private fun <T : CloudXDestroyable> BidResponse.toBidAdSourceResponse(
    bidRequestParams: BidRequestProvider.Params,
    createBidAd: suspend (CreateBidAdParams) -> T,
    auctionId: String,
): BidAdSourceResponse<T> {

    val items = seatBid.asSequence()
        .flatMap { it.bid }
        .map { bid ->

            val price = (bid.price ?: 0.0f).toDouble()
            val adNetworkOriginal = bid.adNetwork
            val adNetwork = when (adNetworkOriginal) {
                AdNetwork.CloudXSecond -> AdNetwork.CloudX
                else -> adNetworkOriginal
            }

            BidAdSourceResponse.Item(
                bid = bid,
                adNetwork = adNetwork,
                adNetworkOriginal = adNetworkOriginal,
                createBidAd = {
                    createBidAd(
                        CreateBidAdParams(
                            placementName = bidRequestParams.placementName,
                            placementId = bidRequestParams.placementId,
                            bid = bid,
                            adNetwork = adNetwork,
                            price = price,
                            auctionId = auctionId
                        )
                    )
                }
            )
        }.sortedBy {
            it.bid.rank
        }.toList()

    return BidAdSourceResponse(items, auctionId)
}
