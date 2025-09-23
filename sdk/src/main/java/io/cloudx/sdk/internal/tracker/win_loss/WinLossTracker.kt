package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.util.ThreadUtils

internal interface WinLossTracker {

    fun setAppKey(appKey: String)

    fun setEndpoint(endpointUrl: String?)

    fun setPayloadMapping(payloadMapping: Map<String, String>)

    fun trySendingPendingWinLossEvents()

    fun markAsLoaded(auctionId: String, bid: Bid)

    fun sendLoss(
        auctionId: String,
        bid: Bid,
        lossReason: LossReason,
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
        ThreadUtils.GlobalIOScope,
        WinLossFieldResolver(),
        WinLossTrackerDb(),
        WinLossTrackerApi()
    )
}
