package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.GlobalScopes
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.db.Database

internal interface WinLossTracker {

    fun setAppKey(appKey: String)

    fun setEndpoint(endpointUrl: String?)

    fun setPayloadMapping(payloadMapping: Map<String, String>)

    fun trySendingPendingWinLossEvents()

    fun sendLoss(
        auctionId: String,
        bid: Bid,
        lossReason: LossReason? = null,
        winnerBidPrice: Float = -1f
    )

    fun sendWin(
        auctionId: String,
        bid: Bid
    )
}

internal fun WinLossTracker(): WinLossTracker = LazySingleInstance

private val LazySingleInstance by lazy {
    WinLossTrackerImpl(
        GlobalScopes.IO,
        WinLossFieldResolver(),
        Database(),
        WinLossTrackerApi()
    )
}
