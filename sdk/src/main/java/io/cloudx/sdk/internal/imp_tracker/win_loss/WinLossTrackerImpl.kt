package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.LossReason
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
    private val db: CloudXDb
) : WinLossTracker {

    private val tag = "WinLossTracker"
    private var endpointUrl: String? = null

    private val trackerApi = WinLossTrackerApi()

    override fun setEndpoint(endpointUrl: String?) {
        this.endpointUrl = endpointUrl
    }

    override fun setConfig(config: Config) {
        WinLossFieldResolver.setConfig(config)
    }

    override fun addBid(
        auctionId: String,
        bid: Bid
    ) {
        AuctionBidManager.addBid(auctionId, bid)
    }

    override fun setWinner(auctionId: String, winningBidId: String, actualWinPrice: Double?) {
        AuctionBidManager.setBidWinner(auctionId, winningBidId, actualWinPrice)
    }

    override fun setBidLoadResult(auctionId: String, bidId: String, success: Boolean, lossReason: LossReason?) {
        AuctionBidManager.setBidLoadResult(auctionId, bidId, success, lossReason)
    }

    override fun processAuctionResults(auctionId: String) {
        scope.launch {
            CloudXLogger.d(tag, "Processing win/loss results for auction: $auctionId")
            AuctionBidManager.processAuctionWinLoss(auctionId, this@WinLossTrackerImpl)
            AuctionBidManager.clearAuction(auctionId)
        }
    }

    override fun sendWin(
        auctionId: String,
        winPrice: Double,
        additionalData: Map<String, Any>
    ) {
        scope.launch {
            WinLossFieldResolver.setWinData(auctionId, winPrice, additionalData)
            val payload = WinLossFieldResolver.buildWinLossPayload(auctionId)
            if (payload != null) {
                trackWinLoss(payload)
                WinLossFieldResolver.clearAuction(auctionId)
            } else {
                CloudXLogger.w(tag, "No payload mapping configured for win/loss notifications")
            }
        }
    }

    override fun sendLoss(
        auctionId: String,
        lossReason: LossReason,
        additionalData: Map<String, Any>
    ) {
        scope.launch {
            WinLossFieldResolver.setLossData(auctionId, lossReason, additionalData)
            val payload = WinLossFieldResolver.buildWinLossPayload(auctionId)
            if (payload != null) {
                trackWinLoss(payload)
                WinLossFieldResolver.clearAuction(auctionId)
            } else {
                CloudXLogger.w(tag, "No payload mapping configured for win/loss notifications")
            }
        }
    }

    private suspend fun trackWinLoss(payload: Map<String, Any>) {
        val eventId = saveToDb(payload)
        CloudXLogger.d(tag, "Saved win/loss event to database with ID: $eventId")

        val endpoint = endpointUrl

        if (endpoint.isNullOrBlank()) {
            CloudXLogger.e(tag, "No endpoint for win/loss notification, event will be retried later")
            return
        }

        val result = trackerApi.send(endpoint, payload)

        if (result is Result.Success) {
            CloudXLogger.d(tag, "Win/loss event sent successfully, removing from database")
            db.cachedWinLossEventDao().delete(eventId)
        } else {
            CloudXLogger.e(tag, "Win/loss event failed to send. Will retry later.")
        }
    }

    override fun trySendingPendingWinLossEvents() {
        scope.launch {
            val cached = db.cachedWinLossEventDao().getAll()
            if (cached.isEmpty()) {
                CloudXLogger.d(tag, "No pending win/loss events to send")
                return@launch
            }
            CloudXLogger.d(tag, "Found ${cached.size} pending win/loss events to retry")
            sendCached(cached)
        }
    }

    private suspend fun sendCached(entries: List<CachedWinLossEvents>) {
        val endpoint = endpointUrl

        if (endpoint.isNullOrBlank()) {
            CloudXLogger.e(tag, "No endpoint configured for win/loss notifications")
            return
        }

        entries.forEach { entry ->
            val payload = parsePayload(entry.payload)
            if (payload != null) {
                val result = trackerApi.send(entry.endpointUrl.ifBlank { endpoint }, payload)
                if (result is Result.Success) {
                    db.cachedWinLossEventDao().delete(entry.id)
                    CloudXLogger.d(tag, "Cached win/loss event sent successfully: ${entry.id}")
                } else {
                    CloudXLogger.e(tag, "Failed to send cached win/loss event: ${entry.id}")
                }
            }
        }
    }

    private suspend fun saveToDb(payload: Map<String, Any>): String {
        val eventId = UUID.randomUUID().toString()
        val payloadJson = JSONObject(payload).toString()

        db.cachedWinLossEventDao().insert(
            CachedWinLossEvents(
                id = eventId,
                endpointUrl = endpointUrl ?: "",
                payload = payloadJson
            )
        )
        return eventId
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
            CloudXLogger.e(tag, "Failed to parse cached payload: ${e.message}")
            null
        }
    }
}
