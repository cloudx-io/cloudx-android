package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.util.ThreadUtils

internal interface WinLossTracker {

    fun setAppKey(appKey: String)

    fun setEndpoint(endpointUrl: String?)

    fun setPayloadMapping(payloadMapping: Map<String, String>)

    fun trySendingPendingWinLossEvents()

    fun saveBidsAsNew(auctionId: String, bids: List<Bid>)

    fun sendEvent(
        auctionId: String,
        bid: Bid,
        event: BidLifecycleEvent,
        winnerBidPrice: Float = -1f
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
