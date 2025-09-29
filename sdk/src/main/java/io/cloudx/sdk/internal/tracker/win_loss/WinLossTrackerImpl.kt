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

    override fun setAppKey(appKey: String) {
        this.appKey = appKey
    }

    override fun setEndpoint(endpointUrl: String?) {
        this.endpointUrl = endpointUrl
    }

    override fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossFieldResolver.setPayloadMapping(payloadMapping)
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
                    isWin = false,
                    loadedBidPrice = bid.price ?: -1f
                )
                val lossPayloadJson = lossPayloadMap?.toJsonString()

                trackerDb.saveNewBid(auctionId, bid, lossPayloadJson)
            }
        }
    }

    override fun markAsLoaded(auctionId: String, bid: Bid) {
        scope.launch {
            val lossPayloadMap = winLossFieldResolver.buildWinLossPayload(
                auctionId = auctionId,
                bid = bid,
                lossReason = LossReason.INTERNAL_ERROR,
                isWin = false,
                loadedBidPrice = bid.price ?: -1f
            )

            val lossPayloadJson = lossPayloadMap?.toJsonString()

            trackerDb.saveLoadedBid(auctionId, bid, lossPayloadJson)
        }
    }

    override fun sendLoss(
        auctionId: String,
        bid: Bid,
        lossReason: LossReason,
        winnerBidPrice: Float
    ) {
        scope.launch {
            val payloadMap = winLossFieldResolver.buildWinLossPayload(
                auctionId = auctionId,
                bid = bid,
                lossReason = lossReason,
                isWin = false,
                loadedBidPrice = winnerBidPrice
            )
            val payloadJson = payloadMap?.toJsonString()
            if (payloadJson == null) {
                CXLogger.w(tag, "Skipping loss event with empty payload (auctionId=$auctionId, bidId=${bid.id})")
                return@launch
            }

            trackerDb.saveLossEvent(auctionId, bid, payloadJson)
            trackWinLoss(payloadJson, auctionId, bid.id)
        }
    }

    override fun sendWin(
        auctionId: String,
        bid: Bid
    ) {
        scope.launch {
            val payloadMap = winLossFieldResolver.buildWinLossPayload(
                auctionId,
                bid,
                lossReason = LossReason.BID_WON,
                isWin = true,
                loadedBidPrice = bid.price ?: -1f
            )
            val payloadJson = payloadMap?.toJsonString()
            if (payloadJson == null) {
                CXLogger.w(tag, "Skipping win event with empty payload (auctionId=$auctionId, bidId=${bid.id})")
                return@launch
            }

            trackerDb.saveWinEvent(auctionId, bid, payloadJson)
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
