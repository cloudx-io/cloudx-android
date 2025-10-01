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
            val lossPayload = entry.lossPayload

            dao.upsert(
                entry.copy(
                    state = CachedWinLossEvents.STATE_LOSS,
                    payload = lossPayload,
                    updatedAt = now
                )
            )
        }

        // NEW --> LOSS
        val newEntries = dao.getAllByState(CachedWinLossEvents.STATE_NEW)
        newEntries.forEach { entry ->
            val lossPayload = entry.lossPayload

            dao.upsert(
                entry.copy(
                    state = CachedWinLossEvents.STATE_LOSS,
                    payload = lossPayload,
                    updatedAt = now
                )
            )
        }
    }

    override suspend fun getPendingEvents(): List<CachedWinLossEvents> {
        return db.cachedWinLossEventDao().getAllExceptStates(
            listOf(
                CachedWinLossEvents.STATE_LOADED,
                CachedWinLossEvents.STATE_NEW
            )
        )
    }

    override suspend fun saveNewBid(
        auctionId: String,
        bid: Bid,
        lossPayload: String?
    ) {
        val dao = db.cachedWinLossEventDao()
        val now = System.currentTimeMillis()

        val event = CachedWinLossEvents(
            id = composeId(auctionId, bid.id),
            auctionId = auctionId,
            bidId = bid.id,
            state = CachedWinLossEvents.STATE_NEW,
            payload = null,
            winPayload = null,
            lossPayload = lossPayload,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(event)
    }

    override suspend fun saveLoadedBid(
        auctionId: String,
        bid: Bid,
        lossPayload: String?
    ) {
        val dao = db.cachedWinLossEventDao()
        val existing = dao.findByAuctionAndBid(auctionId, bid.id)
        val now = System.currentTimeMillis()

        val event = CachedWinLossEvents(
            id = existing?.id ?: composeId(auctionId, bid.id),
            auctionId = auctionId,
            bidId = bid.id,
            state = CachedWinLossEvents.STATE_LOADED,
            payload = null,
            winPayload = null,
            lossPayload = lossPayload,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsert(event)
    }

    override suspend fun saveLossEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    ) {
        val dao = db.cachedWinLossEventDao()
        val existing = dao.findByAuctionAndBid(auctionId, bid.id)
        val now = System.currentTimeMillis()

        val event = CachedWinLossEvents(
            id = existing?.id ?: composeId(auctionId, bid.id),
            auctionId = auctionId,
            bidId = bid.id,
            state = CachedWinLossEvents.STATE_LOSS,
            payload = payload,
            winPayload = null,
            lossPayload = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsert(event)
    }

    override suspend fun saveWinEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    ) {
        val dao = db.cachedWinLossEventDao()
        val existing = dao.findByAuctionAndBid(auctionId, bid.id)
        val now = System.currentTimeMillis()

        val event = CachedWinLossEvents(
            id = existing?.id ?: composeId(auctionId, bid.id),
            auctionId = auctionId,
            bidId = bid.id,
            state = CachedWinLossEvents.STATE_WIN,
            payload = payload,
            winPayload = null,
            lossPayload = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsert(event)
    }

    override suspend fun deleteEvent(auctionId: String, bidId: String) {
        db.cachedWinLossEventDao().deleteByAuctionAndBid(auctionId, bidId)
    }

    private fun composeId(auctionId: String, bidId: String): String = "$auctionId:$bidId"
}