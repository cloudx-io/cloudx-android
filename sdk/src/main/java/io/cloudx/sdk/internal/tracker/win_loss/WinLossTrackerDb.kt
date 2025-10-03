package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents

internal interface WinLossTrackerDb {

    suspend fun getPendingEvents(): List<CachedWinLossEvents>

    suspend fun saveEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    ): Long

    suspend fun deleteEvent(eventId: Long)
}

internal fun WinLossTrackerDb(): WinLossTrackerDb = WinLossTrackerDbImpl(Database())