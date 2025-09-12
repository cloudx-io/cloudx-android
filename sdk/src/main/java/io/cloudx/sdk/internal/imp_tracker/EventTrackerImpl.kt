package io.cloudx.sdk.internal.imp_tracker

import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.imp_tracking.CachedTrackingEvents
import io.cloudx.sdk.internal.imp_tracker.bulk.EventAM
import io.cloudx.sdk.internal.imp_tracker.bulk.EventTrackerBulkApi
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
        CloudXLogger.d(tag, "Saved $eventType event to database with ID: $eventId")

        val endpointUrl = baseEndpoint

        if (endpointUrl.isNullOrBlank()) {
            CloudXLogger.e(tag, "No endpoint for $eventType, event will be retried later")
            return
        }

        val finalUrl = endpointUrl.plus("/${eventType.pathSegment}")
        val result = trackerApi.send(
            finalUrl, encoded, campaignId, eventValue, eventType.code
        )
        
        if (result is io.cloudx.sdk.Result.Success) {
            CloudXLogger.d(tag, "$eventType sent successfully, removing from database")
            db.cachedTrackingEventDao().delete(eventId)
        } else {
            CloudXLogger.e(tag, "$eventType failed to send. Will retry later.")
        }
    }

    override fun trySendingPendingTrackingEvents() {
        scope.launch {
            val cached = db.cachedTrackingEventDao().getAll()
            if (cached.isEmpty()) {
                CloudXLogger.d(tag, "No pending tracking events to send")
                return@launch
            }
            CloudXLogger.d(tag, "Found ${cached.size} pending events to retry")
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
        if (result is io.cloudx.sdk.Result.Success) {
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
