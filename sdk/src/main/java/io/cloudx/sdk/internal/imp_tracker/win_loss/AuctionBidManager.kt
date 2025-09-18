package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.LossReason
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all bids for auctions and handles win/loss determination.
 * Similar to TrackingFieldResolver but specifically for auction bid management.
 */
internal object AuctionBidManager {

    private val tag = "AuctionBidManager"

    // Store all bids for each auction
    private val auctionBids = ConcurrentHashMap<String, MutableList<BidEntry>>()

    // Track auction state
    private val auctionStatus = ConcurrentHashMap<String, AuctionStatus>()

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

    enum class AuctionStatus {
        ACTIVE,      // Auction in progress, collecting bids
        COMPLETED    // Auction completed, winner determined
    }

    /**
     * Add a bid to an auction
     */
    fun addBid(
        auctionId: String,
        bid: Bid
    ) {
        auctionBids.getOrPut(auctionId) { mutableListOf() }.add(BidEntry(bid))
        auctionStatus[auctionId] = AuctionStatus.ACTIVE

        val bidPrice = bid.price?.toDouble() ?: 0.0
        CXLogger.d(
            tag,
            "Added bid ${bid.id} to auction $auctionId (price: $bidPrice, network: ${bid.adNetwork.networkName})"
        )
    }

    /**
     * Mark a specific bid as the winner and all others as lost
     */
    fun setBidWinner(auctionId: String, winningBidId: String): Boolean {
        val bids = auctionBids[auctionId] ?: return false

        var winnerFound = false
        bids.forEach { entry ->
            if (entry.bid.id == winningBidId) {
                entry.status = BidStatus.WON
                winnerFound = true
                CXLogger.d(tag, "Bid ${entry.bid.id} won auction $auctionId")
            } else {
                entry.status = BidStatus.LOST
                entry.lossReason = LossReason.LostToHigherBid
                CXLogger.d(tag, "Bid ${entry.bid.id} lost to higher bid in auction $auctionId")
            }
        }

        if (winnerFound) {
            auctionStatus[auctionId] = AuctionStatus.COMPLETED
        }

        return winnerFound
    }

    /**
     * Mark a bid as successfully loaded (for winner) or failed
     */
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
                CXLogger.d(tag, "Bid $bidId successfully loaded for auction $auctionId")
            } else {
                bid.status = BidStatus.FAILED
                bid.lossReason = lossReason ?: LossReason.TechnicalError
                CXLogger.w(
                    tag,
                    "Bid $bidId failed to load for auction $auctionId: ${bid.lossReason}"
                )
            }
        }
    }


    /**
     * Get winning bid for an auction (only WON status, not LOADED)
     */
    fun getWinningBid(auctionId: String): Bid? {
        return auctionBids[auctionId]
            ?.firstOrNull { it.status == BidStatus.WON }
            ?.bid
    }

    /**
     * Clear auction data (call after processing win/loss)
     */
    fun clearAuction(auctionId: String) {
        auctionBids.remove(auctionId)
        auctionStatus.remove(auctionId)
        CXLogger.d(tag, "Cleared auction data for $auctionId")
    }

    /**
     * Get a specific bid by auction ID and bid ID
     */
    fun getBid(auctionId: String, bidId: String): Bid? {
        return auctionBids[auctionId]?.find { it.bid.id == bidId }?.bid
    }

    /**
     * Get the loss reason for a specific bid
     */
    fun getBidLossReason(auctionId: String, bidId: String): LossReason? {
        return auctionBids[auctionId]
            ?.find { it.bid.id == bidId }
            ?.lossReason
    }

    /**
     * Clear all auction data
     */
    fun clear() {
        auctionBids.clear()
        auctionStatus.clear()
        CXLogger.d(tag, "Cleared all auction data")
    }
}
