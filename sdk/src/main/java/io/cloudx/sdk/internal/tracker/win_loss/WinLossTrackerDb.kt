package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents

// Factory function
internal fun WinLossTrackerDb(): WinLossTrackerDb = WinLossTrackerDb(Database())

// Main class
internal class WinLossTrackerDb(
    private val db: CloudXDb
) {

    suspend fun getPendingEvents(): List<CachedWinLossEvents> {
        return db.cachedWinLossEventDao().getAllUnsent()
    }

    suspend fun saveEvent(
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

    suspend fun deleteEvent(eventId: Long) {
        db.cachedWinLossEventDao().deleteById(eventId)
    }
}
