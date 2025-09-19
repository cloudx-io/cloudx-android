package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

internal class WinLossTrackerImpl(
    private val scope: CoroutineScope,
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

    override fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossFieldResolver.setPayloadMapping(payloadMapping)
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

    override fun sendLoss(
        auctionId: String,
        bid: Bid,
        lossReason: LossReason?,
        winnerBidPrice: Float
    ) {
        scope.launch {
            val payload = winLossFieldResolver.buildWinLossPayload(
                auctionId, bid, lossReason, isWin = false, winnerBidPrice
            )
            if (payload != null) {
                trackWinLoss(payload)
            }
        }
    }

    override fun sendWin(
        auctionId: String,
        bid: Bid
    ) {
        scope.launch {
            val winnerBidPrice = bid.price ?: -1f

            val payload = winLossFieldResolver.buildWinLossPayload(
                auctionId, bid, lossReason = null, isWin = true, winnerBidPrice
            )
            if (payload != null) {
                trackWinLoss(payload)
            }
        }
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
                val result = trackerApi.send(key, endpoint, payload)
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
