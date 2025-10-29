package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.db.imp_tracking.CachedTrackingEvents
import io.cloudx.sdk.internal.tracker.bulk.EventAM
import io.cloudx.sdk.internal.tracker.bulk.EventTrackerBulkApi
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

// Lazy singleton
private val LazySingleInstance by lazy {
    EventTracker(
        ThreadUtils.GlobalIOScope,
        Database()
    )
}

// Factory function
internal fun EventTracker(): EventTracker = LazySingleInstance

// Main class
internal class EventTracker(
    private val scope: CoroutineScope,
    private val db: CloudXDb
) {

    private val logger = CXLogger.forComponent("EventTracker")
    private var baseEndpoint: String? = null
    private var bulkEndpoint: String? = null

    private val trackerApi = EventTrackerApi()
    private val trackerBulkApi = EventTrackerBulkApi()

    fun setEndpoint(endpointUrl: String?) {
        this.baseEndpoint = endpointUrl
    }

    fun send(
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
        logger.d("Saved $eventType event to database with ID: $eventId")

        val endpointUrl = baseEndpoint

        if (endpointUrl.isNullOrBlank()) {
            logger.e("No endpoint for $eventType, event will be retried later")
            return
        }

        val finalUrl = endpointUrl.plus("/${eventType.pathSegment}")
        val result = trackerApi.send(
            finalUrl, encoded, campaignId, eventValue, eventType.code
        )

        if (result is Result.Success) {
            logger.d("$eventType sent successfully, removing from database")
            db.cachedTrackingEventDao().delete(eventId)
        } else {
            logger.e("$eventType failed to send. Will retry later.")
        }
    }

    fun trySendingPendingTrackingEvents() {
        scope.launch {
            val cached = db.cachedTrackingEventDao().getAll()
            if (cached.isEmpty()) {
                logger.d("No pending tracking events to send")
                return@launch
            }
            logger.d("Found ${cached.size} pending events to retry")
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

    fun sendImpression(auctionId: String, bid: Bid) {
        val payload = TrackingFieldResolver.buildPayload(auctionId, bid.id)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            send(impressionId, campaignId, "1", EventType.IMPRESSION)
        }
    }

    fun sendClick(auctionId: String, bid: Bid) {
        val clickCount = ClickCounterTracker.incrementAndGet(auctionId)

        val payload = TrackingFieldResolver.buildPayload(auctionId, bid.id)?.replace(
            auctionId,
            "$auctionId-$clickCount"
        )
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            send(impressionId, campaignId, "1", EventType.CLICK)
        }
    }

    fun sendBidRequest(auctionId: String) {
        val payload = TrackingFieldResolver.buildPayload(auctionId)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            send(impressionId, campaignId, "1", EventType.BID_REQUEST)
        }
    }

    suspend fun sendSdkInit(
        config: Config,
        appKey: String,
        onBasePayloadCreated: (String) -> Unit
    ) {
        val eventId = UUID.randomUUID().toString()

        val bidRequestParams = BidRequestProvider.Params(
            placementId = "",
            adType = AdType.Banner.Standard,
            placementName = "",
            accountId = config.accountId ?: "",
            appKey = appKey,
            appId = config.appId ?: ""
        )

        val bidRequestProvider = BidRequestProvider(bidRequestExtrasProviders = emptyMap())
        val bidRequestParamsJson = bidRequestProvider.invoke(bidRequestParams, eventId)

        TrackingFieldResolver.setRequestData(eventId, bidRequestParamsJson)

        val payload = TrackingFieldResolver.buildPayload(eventId)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            val basePayload = payload.replace(eventId, "{eventId}")
            onBasePayloadCreated(basePayload)

            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            send(impressionId, campaignId, "1", EventType.SDK_INIT)
        }
    }
}
