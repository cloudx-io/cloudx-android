package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents

internal interface WinLossTrackerDb {

    suspend fun convertUnfinishedBidsToLoss()

    suspend fun getPendingEvents(): List<CachedWinLossEvents>

    suspend fun saveNewBid(
        auctionId: String,
        bid: Bid,
        lossPayload: String?
    )

    suspend fun saveLoadEvent(
        auctionId: String,
        bid: Bid,
        lossPayload: String?
    )

    suspend fun saveLossEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    )

    suspend fun saveWinEvent(
        auctionId: String,
        bid: Bid,
        payload: String
    )

    suspend fun deleteEvent(auctionId: String, bidId: String)

    suspend fun markEventAsSent(auctionId: String, bidId: String)
}

internal fun WinLossTrackerDb(): WinLossTrackerDb = WinLossTrackerDbImpl(Database())