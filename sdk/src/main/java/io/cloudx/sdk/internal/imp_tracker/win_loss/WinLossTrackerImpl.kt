package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.CXLogger
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

    private lateinit var appKey: String
    private var endpointUrl: String? = null

    private val winLossFieldResolver = WinLossFieldResolver()
    private val trackerApi = WinLossTrackerApi()

    override fun setAppKey(appKey: String) {
        this.appKey = appKey
    }

    override fun setEndpoint(endpointUrl: String?) {
        this.endpointUrl = endpointUrl
    }

    override fun setConfig(config: Config) {
        winLossFieldResolver.setConfig(config)
    }

    override fun addBid(
        auctionId: String,
        bid: Bid
    ) {
        AuctionBidManager.addBid(auctionId, bid)
    }

    override fun setWinner(auctionId: String, winningBidId: String, actualWinPrice: Double?) {
        // Set the winner status
        AuctionBidManager.setBidWinner(auctionId, winningBidId, actualWinPrice)

        // Automatically send win notification and clean up
        scope.launch {
            val winningBid = AuctionBidManager.getWinningBid(auctionId)
            if (winningBid != null) {
                val winPrice = actualWinPrice ?: winningBid.price?.toDouble() ?: 0.0
                val baseData = mapOf(
                    "bidId" to winningBid.id,
                    "networkName" to winningBid.adNetwork.networkName,
                    "bidPrice" to (winningBid.price?.toDouble() ?: 0.0),
                    "rank" to winningBid.rank,
                    "rawBid" to winningBid.rawJson
                )

                // Send win notification
                sendWin(auctionId, winPrice, baseData)

                // Clean up auction data
                AuctionBidManager.clearAuction(auctionId)
            }
        }
    }

    override fun setBidLoadResult(
        auctionId: String,
        bidId: String,
        success: Boolean,
        lossReason: LossReason?
    ) {
        AuctionBidManager.setBidLoadResult(auctionId, bidId, success, lossReason)
    }

    override fun clearAuction(auctionId: String) {
        AuctionBidManager.clearAuction(auctionId)
    }


    override fun sendWin(
        auctionId: String,
        winPrice: Double,
        additionalData: Map<String, Any>
    ) {
        scope.launch {
            winLossFieldResolver.setWinData(auctionId, winPrice, additionalData)
            val payload = winLossFieldResolver.buildWinLossPayload(auctionId)
            if (payload != null) {
                trackWinLoss(payload)
                winLossFieldResolver.clearAuction(auctionId)
            } else {
                CXLogger.w(tag, "No payload mapping configured for win/loss notifications")
            }
        }
    }

    override fun sendLoss(
        auctionId: String,
        lossReason: LossReason,
        additionalData: Map<String, Any>
    ) {
        scope.launch {
            winLossFieldResolver.setLossData(auctionId, lossReason, additionalData)
            val payload = winLossFieldResolver.buildWinLossPayload(auctionId)
            if (payload != null) {
                trackWinLoss(payload)
                winLossFieldResolver.clearAuction(auctionId)
            } else {
                CXLogger.w(tag, "No payload mapping configured for win/loss notifications")
            }
        }
    }

    private suspend fun trackWinLoss(payload: Map<String, Any>) {
        println("hop: win/loss with payload: $payload")
        val eventId = saveToDb(payload)
        CXLogger.d(tag, "Saved win/loss event to database with ID: $eventId")

        val endpoint = endpointUrl

        if (endpoint.isNullOrBlank()) {
            CXLogger.e(tag, "No endpoint for win/loss notification, event will be retried later")
            return
        }

        val result = trackerApi.send(appKey, endpoint, payload)

        if (result is Result.Success) {
            CXLogger.d(tag, "Win/loss event sent successfully, removing from database")
            db.cachedWinLossEventDao().delete(eventId)
        } else {
            CXLogger.e(tag, "Win/loss event failed to send. Will retry later.")
        }
    }

    override fun trySendingPendingWinLossEvents() {
        scope.launch {
            val cached = db.cachedWinLossEventDao().getAll()
            if (cached.isEmpty()) {
                CXLogger.d(tag, "No pending win/loss events to send")
                return@launch
            }
            CXLogger.d(tag, "Found ${cached.size} pending win/loss events to retry")
            sendCached(cached)
        }
    }

    private suspend fun sendCached(entries: List<CachedWinLossEvents>) {
        val endpoint = endpointUrl

        if (endpoint.isNullOrBlank()) {
            CXLogger.e(tag, "No endpoint configured for win/loss notifications")
            return
        }

        entries.forEach { entry ->
            val payload = parsePayload(entry.payload)
            if (payload != null) {
                val result =
                    trackerApi.send(appKey, entry.endpointUrl.ifBlank { endpoint }, payload)
                if (result is Result.Success) {
                    db.cachedWinLossEventDao().delete(entry.id)
                    CXLogger.d(tag, "Cached win/loss event sent successfully: ${entry.id}")
                } else {
                    CXLogger.e(tag, "Failed to send cached win/loss event: ${entry.id}")
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
            CXLogger.e(tag, "Failed to parse cached payload: ${e.message}")
            null
        }
    }
}
