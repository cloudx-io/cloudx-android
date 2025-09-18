package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
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
        PENDING,     // Bid received, awaiting load result
        LOADED,      // Ad successfully loaded, awaiting impression
        WON,         // Impression received (final win state)
        LOST         // Lost auction or failed to load (check lossReason)
    }
    
    fun addBid(auctionId: String, bid: Bid) {
        auctionBids.getOrPut(auctionId) { mutableListOf() }.add(BidEntry(bid))
    }

    fun setBidWinner(auctionId: String, winningBidId: String) {
        val bids = auctionBids[auctionId] ?: return

        bids.find { it.bid.id == winningBidId }?.let { winner ->
            winner.status = BidStatus.WON
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
                bid.status = BidStatus.LOST
                bid.lossReason = lossReason ?: LossReason.TechnicalError
            }
        }
    }

    fun clearAuction(auctionId: String) {
        auctionBids.remove(auctionId)
    }

    fun getBid(auctionId: String, bidId: String): Bid? {
        return auctionBids[auctionId]?.find { it.bid.id == bidId }?.bid
    }

    fun getLoadedBidPrice(auctionId: String): Float {
        return auctionBids[auctionId]?.find { it.status == BidStatus.LOADED }?.bid?.price ?: -1f
    }

    fun getBidLossReason(auctionId: String, bidId: String): LossReason? {
        return auctionBids[auctionId]
            ?.find { it.bid.id == bidId }
            ?.lossReason
    }
}
