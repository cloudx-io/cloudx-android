package io.cloudx.sdk.internal.core.ad.source.bid

import com.xor.XorEncryption
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.bid.BidResponse
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.EventType
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver.SDK_PARAM_RESPONSE_IN_MILLIS
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.PlacementLoopIndexTracker
import io.cloudx.sdk.internal.state.SdkKeyValueState
import org.json.JSONObject
import java.util.UUID
import kotlin.system.measureTimeMillis

internal interface BidAdSource<T : Destroyable> {

    /**
     * @return the bid or null if no bid
     */
    suspend fun requestBid(): BidAdSourceResponse<T>?
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
    metricsTrackerNew: MetricsTrackerNew,
    createBidAd: suspend (CreateBidAdParams) -> T,
): BidAdSource<T> =
    BidAdSourceImpl(
        provideBidRequest,
        bidRequestParams,
        requestBid,
        cdpApi,
        eventTracker,
        metricsTrackerNew,
        createBidAd
    )

internal class CreateBidAdParams(
    val placementName: String,
    val placementId: String,
    val bidId: String,
    val adm: String,
    val params: Map<String, String>?,
    val burl: String?,
    val nurl: String?,
    val lurl: String?,
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
    private val metricsTrackerNew: MetricsTrackerNew,
    private val createBidAd: suspend (CreateBidAdParams) -> T,
) : BidAdSource<T> {

    private val logTag = "BidAdSourceImpl"

    override suspend fun requestBid(): BidAdSourceResponse<T>? {
        val auctionId = UUID.randomUUID().toString()
        val bidRequestParamsJson = provideBidRequest.invoke(bidRequestParams, auctionId)

        val currentLoopIndex = PlacementLoopIndexTracker.getCount(bidRequestParams.placementName)

        CloudXLogger.d(logTag, "")
        CloudXLogger.d(logTag, "======== loop-index=$currentLoopIndex")
        CloudXLogger.d(logTag, "")
// User Params
        val userParams = SdkKeyValueState.userKeyValues
        CloudXLogger.d(logTag, "user params: $userParams")

        val appParams = SdkKeyValueState.userKeyValues
        CloudXLogger.d(logTag, "app params: $appParams")

        val isCdpDisabled = ResolvedEndpoints.cdpEndpoint.isBlank()

        val enrichedPayload = if (isCdpDisabled) {
            CloudXLogger.d(logTag, "Skipping enrichment.")
            bidRequestParamsJson
        } else {
            CloudXLogger.d(logTag, "Making a call to CDP")
            when (val enrichResult = cdpApi.enrich(bidRequestParamsJson)) {
                is Result.Success -> {
                    CloudXLogger.d(logTag, "Received enriched data from CDP")

                    val lambdaTargeting = extractLambdaTargetingFromRoot(enrichResult.value)
                    if (lambdaTargeting.isNotEmpty()) {
                        CloudXLogger.d(
                            logTag,
                            "CDP Targeting: " + lambdaTargeting.entries.joinToString { "{${it.key}=${it.value}}" }
                        )
                        CloudXLogger.d(logTag, "")
                    }

                    enrichResult.value
                }

                is Result.Failure -> {
                    CloudXLogger.e(
                        logTag,
                        "CDP enrichment failed: ${enrichResult.value.effectiveMessage}. Using original payload."
                    )
                    bidRequestParamsJson
                }
            }
        }

        CloudXLogger.d(
            logTag,
            "Sending BidRequest [loop-index=$currentLoopIndex] for placementId: ${bidRequestParams.placementId}"
        )

        val result: Result<BidResponse, CLXError>
        val bidRequestLatencyMillis = measureTimeMillis {
            result = requestBid.invoke(bidRequestParams.appKey, enrichedPayload)
        }

        metricsTrackerNew.trackNetworkCall(MetricsType.Network.BidRequest, bidRequestLatencyMillis)

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
            is Result.Failure -> {
                CloudXLogger.e(logTag, result.value.effectiveMessage)
                null
            }

            is Result.Success -> {
                val bidAdSourceResponse = result.value.toBidAdSourceResponse(bidRequestParams, createBidAd)

                if (bidAdSourceResponse.bidItemsByRank.isEmpty()) {
                    CloudXLogger.d(logTag, "NO_BID")
                } else {
                    val bidDetails = bidAdSourceResponse.bidItemsByRank.joinToString(separator = ",\n") {
                            "\"bidder\": \"${it.adNetworkOriginal}\", cpm: ${it.priceRaw}, rank: ${it.rank}"
                        }
                    CloudXLogger.d(
                        logTag,
                        "Bid Success — received ${bidAdSourceResponse.bidItemsByRank.size} bid(s): [$bidDetails]"
                    )

                    TrackingFieldResolver.setSdkParam(
                        auctionId,
                        SDK_PARAM_RESPONSE_IN_MILLIS,
                        bidRequestLatencyMillis.toString()
                    )
                }

                bidAdSourceResponse
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
                            params = bid.adapterExtras,
                            burl = bid.burl,
                            nurl = bid.nurl,
                            lurl = bid.lurl,
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

fun extractLambdaTargetingFromRoot(json: JSONObject): Map<String, String> {
    val targeting = mutableMapOf<String, String>()

    val ext = json.optJSONObject("ext") ?: return targeting
    val prebid = ext.optJSONObject("prebid") ?: return targeting
    val adServerTargeting = prebid.optJSONArray("adservertargeting") ?: return targeting

    for (i in 0 until adServerTargeting.length()) {
        val item = adServerTargeting.optJSONObject(i) ?: continue
        if (item.optString("source") == "lambda") {
            val key = item.optString("key")
            val value = item.optString("value")
            if (key.isNotBlank() && value.isNotBlank()) {
                targeting[key] = value
            }
        }
    }

    return targeting
}