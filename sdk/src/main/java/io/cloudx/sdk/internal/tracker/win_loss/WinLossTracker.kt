package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

// Lazy singleton
private val LazySingleInstance by lazy {
    WinLossTracker(
        ThreadUtils.GlobalIOScope,
        WinLossFieldResolver(),
        WinLossTrackerDb(),
        WinLossTrackerApi()
    )
}

// Factory function
internal fun WinLossTracker(): WinLossTracker = LazySingleInstance

// Main class
internal class WinLossTracker(
    private val scope: CoroutineScope,
    private val winLossFieldResolver: WinLossFieldResolver,
    private val trackerDb: WinLossTrackerDb,
    private val trackerApi: WinLossTrackerApi
) {

    private val logger = CXLogger.forComponent("WinLossTracker")

    private var appKey: String? = null
    private var endpointUrl: String? = null

    fun setAppKey(appKey: String) {
        this.appKey = appKey
    }

    fun setEndpoint(endpointUrl: String?) {
        this.endpointUrl = endpointUrl
    }

    fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossFieldResolver.setPayloadMapping(payloadMapping)
    }

    fun trySendingPendingWinLossEvents() {
        scope.launch {
            val pendingEvents = trackerDb.getPendingEvents()
            if (pendingEvents.isNotEmpty()) {
                sendCached(pendingEvents)
            }
        }
    }

    /**
     * Sends a win/loss tracking event.
     *
     * @param auctionId Unique identifier for the auction
     * @param bid The bid associated with this event
     * @param event The bid lifecycle event type
     * @param lossReason The reason for loss (if applicable)
     * @param error The error that caused the failure. Should be provided for LOSS events
     *              to enable detailed error tracking and analytics. Null for successful events.
     */
    fun sendEvent(
        auctionId: String,
        bid: Bid,
        event: BidLifecycleEvent,
        lossReason: LossReason,
        error: CloudXError? = null
    ) {
        scope.launch {
            val payloadMap = winLossFieldResolver.buildWinLossPayload(
                auctionId = auctionId,
                bid = bid,
                lossReason = lossReason,
                bidLifecycleEvent = event,
                error = error
            )
            val payloadJson = payloadMap?.toJsonString()
            if (payloadJson.isNullOrEmpty()) {
                logger.w("Skipping $event event with empty payload (auctionId=$auctionId, bidId=${bid.id})")
                return@launch
            }

            val eventId = trackerDb.saveEvent(auctionId, bid, payloadJson)
            trackWinLoss(payloadJson, eventId)
        }
    }

    private suspend fun sendCached(entries: List<CachedWinLossEvents>) {
        entries.forEach { entry ->
            val payloadJson = entry.payload
            if (payloadJson.isEmpty()) {
                logger.w("Skipping cached entry due to empty payload (eventId=${entry.id})")
                return@forEach
            }
            trackWinLoss(payloadJson, entry.id)
        }
    }

    private suspend fun trackWinLoss(payloadJson: String, eventId: Long) {
        val endpoint = endpointUrl
        val key = appKey

        if (endpoint.isNullOrBlank() || key.isNullOrBlank()) {
            logger.w("Missing endpoint or app key for win/loss tracking")
            return
        }

        val payload = parsePayload(payloadJson) ?: return
        val result = trackerApi.send(key, endpoint, payload)

        if (result is Result.Success) {
            trackerDb.deleteEvent(eventId)
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
            logger.e("Failed to parse win/loss payload JSON", e)
            ErrorReportingService().sendErrorEvent(
                errorMessage = "Win/Loss payload JSON parsing failed: ${e.message}",
                errorDetails = e.stackTraceToString()
            )
            null
        }
    }

    private fun Map<String, Any>.toJsonString(): String = JSONObject(this).toString()
}
