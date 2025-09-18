package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.LossReason
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all bids for auctions and handles win/loss determination.
 */
internal class AuctionBidManager {

    private val auctionBids = ConcurrentHashMap<String, MutableList<BidEntry>>()

    private class BidEntry(
        val bid: Bid
    ) {
        var status: BidStatus = BidStatus.PENDING
        var lossReason: LossReason? = null
    }

    enum class BidStatus {
        PENDING,     // Bid received, awaiting auction result
        WON,         // Bid won the auction
        LOST,        // Bid lost the auction
        LOADED,      // Winning bid successfully loaded ad
        FAILED       // Bid failed (technical error, load failure, etc.)
    }
    
    fun addBid(auctionId: String, bid: Bid) {
        auctionBids.getOrPut(auctionId) { mutableListOf() }.add(BidEntry(bid))
    }

    fun setBidWinner(auctionId: String, winningBidId: String) {
        val bids = auctionBids[auctionId] ?: return

        bids.forEach { entry ->
            if (entry.bid.id == winningBidId) {
                entry.status = BidStatus.WON
            } else {
                entry.status = BidStatus.LOST
                entry.lossReason = LossReason.LostToHigherBid
            }
        }
    }

    fun setBidLoadResult(
        auctionId: String,
        bidId: String,
        success: Boolean,
        lossReason: LossReason? = null
    ) {
        val bids = auctionBids[auctionId] ?: return

        bids.find { it.bid.id == bidId }?.let { bid ->
            if (success) {
                bid.status = BidStatus.LOADED
            } else {
                bid.status = BidStatus.FAILED
                bid.lossReason = lossReason ?: LossReason.TechnicalError
            }
        }
    }

    fun getWinningBid(auctionId: String): Bid? {
        return auctionBids[auctionId]
            ?.firstOrNull { it.status == BidStatus.WON }
            ?.bid
    }

    fun clearAuction(auctionId: String) {
        auctionBids.remove(auctionId)
    }

    fun getBid(auctionId: String, bidId: String): Bid? {
        return auctionBids[auctionId]?.find { it.bid.id == bidId }?.bid
    }

    fun getBidLossReason(auctionId: String, bidId: String): LossReason? {
        return auctionBids[auctionId]
            ?.find { it.bid.id == bidId }
            ?.lossReason
    }

    fun clear() {
        auctionBids.clear()
    }
}
