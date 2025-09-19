package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.imp_tracking.CachedTrackingEvents
import io.cloudx.sdk.internal.tracker.bulk.EventAM
import io.cloudx.sdk.internal.tracker.bulk.EventTrackerBulkApi
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

internal class EventTrackerImpl(
    private val scope: CoroutineScope,
    private val db: CloudXDb
) : EventTracker {

    private val tag = "EventTracker"
    private var baseEndpoint: String? = null
    private var bulkEndpoint: String? = null

    private val trackerApi = EventTrackerApi()
    private val trackerBulkApi = EventTrackerBulkApi()

    override fun setEndpoint(endpointUrl: String?) {
        this.baseEndpoint = endpointUrl
    }

    override fun send(
        encoded: String, campaignId: String, eventValue: String, eventType: EventType
    ) {
        scope.launch {
            trackEvent(encoded, campaignId, eventValue, eventType)
        }
    }

    private suspend fun trackEvent(
        encoded: String, campaignId: String, eventValue: String, eventType: EventType
    ) {
        val eventId = saveToDb(encoded, campaignId, eventValue, eventType)
        CXLogger.d(tag, "Saved $eventType event to database with ID: $eventId")

        val endpointUrl = baseEndpoint

        if (endpointUrl.isNullOrBlank()) {
            CXLogger.e(tag, "No endpoint for $eventType, event will be retried later")
            return
        }

        val finalUrl = endpointUrl.plus("/${eventType.pathSegment}")
        val result = trackerApi.send(
            finalUrl, encoded, campaignId, eventValue, eventType.code
        )
        
        if (result is Result.Success) {
            CXLogger.d(tag, "$eventType sent successfully, removing from database")
            db.cachedTrackingEventDao().delete(eventId)
        } else {
            CXLogger.e(tag, "$eventType failed to send. Will retry later.")
        }
    }

    override fun trySendingPendingTrackingEvents() {
        scope.launch {
            val cached = db.cachedTrackingEventDao().getAll()
            if (cached.isEmpty()) {
                CXLogger.d(tag, "No pending tracking events to send")
                return@launch
            }
            CXLogger.d(tag, "Found ${cached.size} pending events to retry")
            sendBulk(cached)
        }
    }

    private suspend fun sendBulk(entries: List<CachedTrackingEvents>) {
        val endpointUrl = bulkEndpoint

        if (endpointUrl.isNullOrBlank()) {
            return
        }

        val items = entries.map { entry ->
            EventAM(
                impression = entry.encoded,
                campaignId = entry.campaignId,
                eventValue = entry.eventValue,
                eventName = entry.eventName,
                type = entry.type
            )
        }

        val result = trackerBulkApi.send(endpointUrl, items)
        if (result is Result.Success) {
            entries.forEach {
                db.cachedTrackingEventDao().delete(it.id)
            }
        }
    }

    private suspend fun saveToDb(
        encoded: String, campaignId: String, eventValue: String, eventType: EventType
    ): String {
        val eventId = UUID.randomUUID().toString()
        db.cachedTrackingEventDao().insert(
            CachedTrackingEvents(
                id = eventId,
                encoded = encoded,
                campaignId = campaignId,
                eventValue = eventValue,
                eventName = eventType.code,
                type = eventType.pathSegment
            )
        )
        return eventId
    }
}
