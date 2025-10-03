package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents

internal class WinLossTrackerDbImpl(
    private val db: CloudXDb
) : WinLossTrackerDb {

    override suspend fun getPendingEvents(): List<CachedWinLossEvents> {
        return db.cachedWinLossEventDao().getAllUnsent()
    }

    override suspend fun saveEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    ): Long {
        val event = CachedWinLossEvents(
            id = 0L,
            auctionId = auctionId,
            bidId = bid.id,
            payload = payload,
            createdAt = System.currentTimeMillis()
        )
        return db.cachedWinLossEventDao().insert(event)
    }

    override suspend fun deleteEvent(eventId: Long) {
        db.cachedWinLossEventDao().deleteById(eventId)
    }
}