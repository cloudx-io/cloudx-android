package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class WinLossTrackerImpl(
    private val scope: CoroutineScope,
    private val winLossFieldResolver: WinLossFieldResolver,
    private val trackerDb: WinLossTrackerDb,
    private val trackerApi: WinLossTrackerApi
) : WinLossTracker {

    private val tag = "WinLossTracker"

    private var appKey: String? = null
    private var endpointUrl: String? = null
    private var eventsMapping: Map<String, Map<String, String>> = emptyMap()

    override fun setAppKey(appKey: String) {
        this.appKey = appKey
    }

    override fun setEndpoint(endpointUrl: String?) {
        this.endpointUrl = endpointUrl
    }

    override fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossFieldResolver.setPayloadMapping(payloadMapping)
    }

    override fun setEventsMapping(eventsMapping: Map<String, Map<String, String>>) {
        this.eventsMapping = eventsMapping
    }

    override fun trySendingPendingWinLossEvents() {
        scope.launch {
            trackerDb.convertUnfinishedBidsToLoss()

            val pending = trackerDb.getPendingEvents()
            if (pending.isEmpty()) {
                return@launch
            }
            sendCached(pending)
        }
    }

    override fun saveBidsAsNew(auctionId: String, bids: List<Bid>) {
        scope.launch {
            bids.forEach { bid ->
                val lossPayloadMap = winLossFieldResolver.buildWinLossPayload(
                    auctionId = auctionId,
                    bid = bid,
                    lossReason = LossReason.INTERNAL_ERROR,
                    bidLifecycleEvent = BidLifecycleEvent.NEW,
                    loadedBidPrice = bid.price ?: -1f
                )
                val lossPayloadJson = lossPayloadMap?.toJsonString()

                trackerDb.saveNewBid(auctionId, bid, lossPayloadJson)
            }
        }
    }

    override fun sendEvent(
        auctionId: String,
        bid: Bid,
        event: BidLifecycleEvent,
        winnerBidPrice: Float
    ) {
        scope.launch {
            val bidderName = bid.adNetwork.networkName.lowercase()

            val bidderEventsConfig = eventsMapping[bidderName] ?: eventsMapping["default"]

            if (bidderEventsConfig == null) {
                CXLogger.d(tag, "No events config found for bidder '$bidderName', skipping ${event.eventKey} event")
                return@launch
            }

            val eventKey = event.eventKey
            val isEventEnabled = bidderEventsConfig[eventKey]?.toBoolean() ?: false
            if (!isEventEnabled) {
                CXLogger.d(tag, "Event '$eventKey' is disabled for bidder '$bidderName', skipping")
                return@launch
            }

            val effectiveLossReason = when (event) {
                BidLifecycleEvent.LOAD_START -> LossReason.BID_WON
                BidLifecycleEvent.LOAD_SUCCESS -> LossReason.BID_WON
                BidLifecycleEvent.RENDER_SUCCESS -> LossReason.BID_WON
                BidLifecycleEvent.LOAD_FAIL -> LossReason.INTERNAL_ERROR
                BidLifecycleEvent.LOSS -> LossReason.LOST_TO_HIGHER_BID
                BidLifecycleEvent.NEW -> LossReason.INTERNAL_ERROR
            }

            val payloadMap = winLossFieldResolver.buildWinLossPayload(
                auctionId = auctionId,
                bid = bid,
                lossReason = effectiveLossReason,
                bidLifecycleEvent = event,
                loadedBidPrice = when (event) {
                    BidLifecycleEvent.LOAD_SUCCESS -> bid.price ?: -1f
                    BidLifecycleEvent.RENDER_SUCCESS -> bid.price ?: -1f
                    else -> winnerBidPrice
                }
            )
            val payloadJson = payloadMap?.toJsonString()
            if (payloadJson == null) {
                CXLogger.w(tag, "Skipping ${event.eventKey} event with empty payload (auctionId=$auctionId, bidId=${bid.id})")
                return@launch
            }

            when (event) {
                BidLifecycleEvent.RENDER_SUCCESS -> {
                    trackerDb.saveWinEvent(auctionId, bid, payloadJson)
                }
                BidLifecycleEvent.LOAD_SUCCESS -> {
                    trackerDb.saveLoadedBid(auctionId, bid, payloadJson)
                }
                else -> {
                    trackerDb.saveLossEvent(auctionId, bid, payloadJson)
                }
            }

            trackWinLoss(payloadJson, auctionId, bid.id)
        }
    }

    private suspend fun sendCached(entries: List<CachedWinLossEvents>) {
        entries.forEach { entry ->
            val payloadJson = entry.payload
            if (payloadJson.isNullOrEmpty()) {
                CXLogger.w(tag, "Skipping cached entry due to empty payload (auctionId=${entry.auctionId}, bidId=${entry.bidId}, state=${entry.state})")
                return@forEach
            }
            trackWinLoss(payloadJson, entry.auctionId, entry.bidId)
        }
    }

    private suspend fun trackWinLoss(payloadJson: String, auctionId: String, bidId: String) {
        val endpoint = endpointUrl
        val key = appKey

        if (endpoint.isNullOrBlank() || key.isNullOrBlank()) {
            CXLogger.w(tag, "Missing endpoint or app key for win/loss tracking")
            return
        }

        val payload = parsePayload(payloadJson) ?: return
        val result = trackerApi.send(key, endpoint, payload)

        if (result is Result.Success) {
            trackerDb.deleteEvent(auctionId, bidId)
        }
    }

    private fun parsePayload(payloadJson: String): Map<String, Any>? {
        return try {
            val json = JSONObject(payloadJson)
            val result = mutableMapOf<String, Any>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.get(key)
            }
            result
        } catch (e: Exception) {
            CXLogger.e(tag, "Failed to parse win/loss payload JSON", e)
            null
        }
    }

    private fun Map<String, Any>.toJsonString(): String = JSONObject(this).toString()
}
