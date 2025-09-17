package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.GlobalScopes
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.Database

internal interface WinLossTracker {

    fun trySendingPendingWinLossEvents()

    fun setEndpoint(endpointUrl: String?)

    fun setConfig(config: Config)

    fun sendWin(
        auctionId: String,
        winPrice: Double,
        additionalData: Map<String, Any> = emptyMap()
    )

    fun sendLoss(
        auctionId: String,
        lossReason: LossReason,
        additionalData: Map<String, Any> = emptyMap()
    )

    /**
     * Add a bid to an auction for tracking
     */
    fun addBid(
        auctionId: String,
        bid: Bid
    )

    /**
     * Set the winning bid for an auction
     */
    fun setWinner(auctionId: String, winningBidId: String, actualWinPrice: Double? = null)

    /**
     * Mark bid load result (success/failure)
     */
    fun setBidLoadResult(auctionId: String, bidId: String, success: Boolean, lossReason: LossReason? = null)

    /**
     * Clear auction data (for cleanup when no winner)
     */
    fun clearAuction(auctionId: String)
}

internal fun WinLossTracker(): WinLossTracker = LazySingleInstance

private val LazySingleInstance by lazy {
    WinLossTrackerImpl(
        GlobalScopes.IO,
        Database()
    )
}
