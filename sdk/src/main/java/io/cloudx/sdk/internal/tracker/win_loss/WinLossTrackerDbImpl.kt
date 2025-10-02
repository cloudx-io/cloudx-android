package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents

internal class WinLossTrackerDbImpl(
    private val db: CloudXDb
) : WinLossTrackerDb {

    override suspend fun convertUnfinishedBidsToLoss() {
        val dao = db.cachedWinLossEventDao()
        val now = System.currentTimeMillis()

        // LOADED --> LOSS
        val loadedEntries = dao.getAllByState(CachedWinLossEvents.STATE_LOADED)
        loadedEntries.forEach { entry ->
            dao.upsert(
                entry.copy(
                    state = CachedWinLossEvents.STATE_LOSS,
                    payload = entry.lossPayload,
                    sent = false,
                    updatedAt = now
                )
            )
        }

        // NEW --> LOSS
        val newEntries = dao.getAllByState(CachedWinLossEvents.STATE_NEW)
        newEntries.forEach { entry ->
            dao.upsert(
                entry.copy(
                    state = CachedWinLossEvents.STATE_LOSS,
                    payload = entry.lossPayload,
                    sent = false,
                    updatedAt = now
                )
            )
        }
    }

    override suspend fun getPendingEvents(): List<CachedWinLossEvents> {
        return db.cachedWinLossEventDao().getUnsentEvents(
            excludeStates = listOf(CachedWinLossEvents.STATE_NEW, CachedWinLossEvents.STATE_LOADED)
        )
    }

    override suspend fun saveNewBid(
        auctionId: String,
        bid: Bid,
        payload: String?
    ) {
        val dao = db.cachedWinLossEventDao()
        val now = System.currentTimeMillis()

        val event = CachedWinLossEvents(
            id = composeId(auctionId, bid.id),
            auctionId = auctionId,
            bidId = bid.id,
            state = CachedWinLossEvents.STATE_NEW,
            payload = null,
            lossPayload = payload,
            sent = true,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(event)
    }

    override suspend fun saveLoadEvent(
        auctionId: String,
        bid: Bid,
        payload: String?
    ) {
        // For LOAD events, the payload is the load success payload
        // We need to generate a loss payload for potential conversion
        // For now, we'll pass null and let the existing lossPayload be preserved
        saveEvent(auctionId, bid, CachedWinLossEvents.STATE_LOADED, payload = payload)
    }

    override suspend fun saveLossEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    ) = saveEvent(auctionId, bid, CachedWinLossEvents.STATE_LOSS, payload = payload)

    override suspend fun saveWinEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    ) = saveEvent(auctionId, bid, CachedWinLossEvents.STATE_WIN, payload = payload)

    private suspend fun saveEvent(
        auctionId: String,
        bid: Bid,
        state: String,
        payload: String? = null,
        lossPayload: String? = null
    ) {
        val dao = db.cachedWinLossEventDao()
        val existing = dao.findByAuctionAndBid(auctionId, bid.id)
        val now = System.currentTimeMillis()

        val event = CachedWinLossEvents(
            id = existing?.id ?: composeId(auctionId, bid.id),
            auctionId = auctionId,
            bidId = bid.id,
            state = state,
            payload = payload,
            lossPayload = lossPayload ?: existing?.lossPayload,
            sent = false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsert(event)
    }

    override suspend fun markEventAsSent(auctionId: String, bidId: String) {
        val dao = db.cachedWinLossEventDao()
        val existing = dao.findByAuctionAndBid(auctionId, bidId)

        existing?.let { event ->
            val updatedEvent = event.copy(
                sent = true,
                updatedAt = System.currentTimeMillis()
            )

            dao.upsert(updatedEvent)

            if (shouldDeleteAfterSent(updatedEvent)) {
                dao.deleteByAuctionAndBid(auctionId, bidId)
            }
        }
    }

    private fun shouldDeleteAfterSent(event: CachedWinLossEvents): Boolean {
        return event.sent && (
            event.state == CachedWinLossEvents.STATE_WIN ||
            event.state == CachedWinLossEvents.STATE_LOSS
        )
    }

    private fun composeId(auctionId: String, bidId: String): String = "$auctionId:$bidId"
}