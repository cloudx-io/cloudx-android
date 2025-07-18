package io.cloudx.sdk.internal.imp_tracker

import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.appfgduration.AppForegroundDurationService
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.imp_tracking.CachedTrackingEvents
import io.cloudx.sdk.internal.imp_tracker.bulk.EventAM
import io.cloudx.sdk.internal.imp_tracker.bulk.EventTrackerBulkApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

internal class EventTrackerImpl(
    private val scope: CoroutineScope,
    private val appForegroundDurationService: AppForegroundDurationService,
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

        val endpointUrl = baseEndpoint

        if (endpointUrl.isNullOrBlank()) {
            Logger.e(tag, "No endpoint for $eventType, caching event")
            saveToDb(encoded, campaignId, eventValue, eventType)
            return
        }

        val finalUrl = endpointUrl.plus("/${eventType.pathSegment}")
        val result = trackerApi.send(
            finalUrl, encoded, campaignId, eventValue, eventType.code
        )
        if (result is io.cloudx.sdk.Result.Success) {
            Logger.d(tag, "$eventType sent successfully.")
        } else {
            Logger.e(tag, "$eventType failed to send. Caching for retry later.")
            saveToDb(encoded, campaignId, eventValue, eventType)
        }
    }

    override fun trySendingPendingTrackingEvents() {
        scope.launch {
            val cached = db.cachedTrackingEventDao().getAll()
            if (cached.isEmpty()) {
                Logger.d(tag, "No pending tracking events to send")
                return@launch
            }
            Logger.d(tag, "Found ${cached.size} pending events to retry")
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
    ) {
        db.cachedTrackingEventDao().insert(
            CachedTrackingEvents(
                id = UUID.randomUUID().toString(),
                encoded = encoded,
                campaignId = campaignId,
                eventValue = eventValue,
                eventName = eventType.code,
                type = eventType.pathSegment
            )
        )
    }
}
