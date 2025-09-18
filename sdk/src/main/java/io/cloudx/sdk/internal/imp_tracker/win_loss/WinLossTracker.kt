package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.GlobalScopes
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.Database

internal interface WinLossTracker {

    fun trySendingPendingWinLossEvents()

    fun setAppKey(appKey: String)

    fun setEndpoint(endpointUrl: String?)

    fun setConfig(config: Config)

    fun sendWin(
        auctionId: String,
        bidId: String
    )

    fun sendLoss(
        auctionId: String,
        bidId: String
    )

    fun addBid(
        auctionId: String,
        bid: Bid
    )

    fun setBidLoadResult(
        auctionId: String,
        bidId: String,
        success: Boolean,
        lossReason: LossReason? = null
    )

    fun setWinner(auctionId: String, winningBidId: String)

    fun clearAuction(auctionId: String)
}

internal fun WinLossTracker(): WinLossTracker = LazySingleInstance

private val LazySingleInstance by lazy {
    WinLossTrackerImpl(
        GlobalScopes.IO,
        Database()
    )
}
