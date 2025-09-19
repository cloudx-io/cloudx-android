package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

internal class WinLossTrackerImpl(
    private val scope: CoroutineScope,
    private val auctionBidManager: AuctionBidManager,
    private val winLossFieldResolver: WinLossFieldResolver,
    private val db: CloudXDb,
    private val trackerApi: WinLossTrackerApi
) : WinLossTracker {

    private var appKey: String? = null
    private var endpointUrl: String? = null

    override fun setAppKey(appKey: String) {
        this.appKey = appKey
    }

    override fun setEndpoint(endpointUrl: String?) {
        this.endpointUrl = endpointUrl
    }

    override fun setConfig(config: Config) {
        winLossFieldResolver.setConfig(config)
    }

    override fun trySendingPendingWinLossEvents() {
        scope.launch {
            val cached = db.cachedWinLossEventDao().getAll()
            if (cached.isEmpty()) {
                return@launch
            }
            sendCached(cached)
        }
    }

    override fun addBid(
        auctionId: String,
        bid: Bid
    ) {
        auctionBidManager.addBid(auctionId, bid)
    }

    override fun setBidLoadResult(
        auctionId: String,
        bidId: String,
        success: Boolean,
        lossReason: LossReason?
    ) {
        auctionBidManager.setBidLoadResult(auctionId, bidId, success, lossReason)
    }

    override fun setWinner(auctionId: String, winningBidId: String) {
        auctionBidManager.setBidWinner(auctionId, winningBidId)
    }

    override fun sendLoss(
        auctionId: String,
        bidId: String
    ) {
        scope.launch {
            val bid = auctionBidManager.getBid(auctionId, bidId)
            val lossReason = auctionBidManager.getBidLossReason(auctionId, bidId)
            val loadedBidPrice = auctionBidManager.getLoadedBidPrice(auctionId)

            if (bid == null) {
                return@launch
            }

            val payload = winLossFieldResolver.buildWinLossPayload(
                auctionId, bid, lossReason, isWin = false, loadedBidPrice
            )
            if (payload != null) {
                trackWinLoss(payload)
            }
        }
    }

    override fun sendWin(
        auctionId: String,
        bidId: String
    ) {
        scope.launch {
            val bid = auctionBidManager.getBid(auctionId, bidId) ?: return@launch
            val loadedBidPrice = auctionBidManager.getLoadedBidPrice(auctionId)

            val payload = winLossFieldResolver.buildWinLossPayload(
                auctionId, bid, lossReason = null, isWin = true, loadedBidPrice
            )
            if (payload != null) {
                trackWinLoss(payload)
            }
            auctionBidManager.clearAuction(auctionId)
        }
    }

    override fun clearAuction(auctionId: String) {
        auctionBidManager.clearAuction(auctionId)
    }

    private suspend fun trackWinLoss(payload: Map<String, Any>) {
        val eventId = saveToDb(payload)
        val endpoint = endpointUrl
        val key = appKey

        if (endpoint.isNullOrBlank() || key.isNullOrBlank()) {
            return
        }

        val result = trackerApi.send(key, endpoint, payload)

        if (result is Result.Success) {
            db.cachedWinLossEventDao().delete(eventId)
        }
    }

    private suspend fun saveToDb(payload: Map<String, Any>): String {
        val eventId = UUID.randomUUID().toString()
        val payloadJson = JSONObject(payload).toString()

        db.cachedWinLossEventDao().insert(
            CachedWinLossEvents(
                id = eventId,
                payload = payloadJson
            )
        )
        return eventId
    }

    private suspend fun sendCached(entries: List<CachedWinLossEvents>) {
        val endpoint = endpointUrl
        val key = appKey

        if (endpoint.isNullOrBlank() || key.isNullOrBlank()) {
            return
        }

        entries.forEach { entry ->
            val payload = parsePayload(entry.payload)
            if (payload != null) {
                val result =
                    trackerApi.send(key, endpoint, payload)
                if (result is Result.Success) {
                    db.cachedWinLossEventDao().delete(entry.id)
                }
            }
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
            null
        }
    }
}
