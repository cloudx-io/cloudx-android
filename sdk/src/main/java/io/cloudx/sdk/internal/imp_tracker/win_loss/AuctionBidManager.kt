package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.CloudXLogger
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
        var actualWinPrice: Double? = null
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
        COMPLETED,   // Auction completed, winner determined
        CANCELLED    // Auction cancelled
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
        CloudXLogger.d(tag, "Added bid ${bid.id} to auction $auctionId (price: $bidPrice, network: ${bid.adNetwork.networkName})")
    }

    /**
     * Mark a specific bid as the winner and all others as lost
     */
    fun setBidWinner(auctionId: String, winningBidId: String, actualWinPrice: Double? = null): Boolean {
        val bids = auctionBids[auctionId] ?: return false

        var winnerFound = false
        bids.forEach { entry ->
            if (entry.bid.id == winningBidId) {
                entry.status = BidStatus.WON
                entry.actualWinPrice = actualWinPrice
                    ?: entry.bid.price?.toDouble()
                winnerFound = true
                CloudXLogger.d(tag, "Bid ${entry.bid.id} won auction $auctionId")
            } else {
                entry.status = BidStatus.LOST
                entry.lossReason = LossReason.LostToHigherBid
                CloudXLogger.d(tag, "Bid ${entry.bid.id} lost to higher bid in auction $auctionId")
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
    fun setBidLoadResult(auctionId: String, bidId: String, success: Boolean, lossReason: LossReason? = null) {
        val bids = auctionBids[auctionId] ?: return

        bids.find { it.bid.id == bidId }?.let { bid ->
            if (success) {
                bid.status = BidStatus.LOADED
                CloudXLogger.d(tag, "Bid $bidId successfully loaded for auction $auctionId")
            } else {
                bid.status = BidStatus.FAILED
                bid.lossReason = lossReason ?: LossReason.TechnicalError
                CloudXLogger.w(tag, "Bid $bidId failed to load for auction $auctionId: ${bid.lossReason}")
            }
        }
    }

    /**
     * Cancel an auction - mark all bids as lost with technical error
     */
    fun cancelAuction(auctionId: String) {
        val bids = auctionBids[auctionId] ?: return

        bids.forEach { bid ->
            if (bid.status == BidStatus.PENDING) {
                bid.status = BidStatus.LOST
                bid.lossReason = LossReason.TechnicalError
            }
        }

        auctionStatus[auctionId] = AuctionStatus.CANCELLED
        CloudXLogger.d(tag, "Cancelled auction $auctionId")
    }

    /**
     * Get winning bid for an auction
     */
    fun getWinningBid(auctionId: String): Bid? {
        return auctionBids[auctionId]
            ?.firstOrNull { it.status == BidStatus.WON || it.status == BidStatus.LOADED }
            ?.bid
    }

    fun getWinningBidPrice(auctionId: String): Double? {
        return auctionBids[auctionId]
            ?.firstOrNull { it.status == BidStatus.WON || it.status == BidStatus.LOADED }
            ?.let { it.actualWinPrice ?: it.bid.price?.toDouble() }
    }

    /**
     * Get all losing bids for an auction
     */
    fun getLosingBids(auctionId: String): List<Bid> {
        return auctionBids[auctionId]
            ?.filter { it.status == BidStatus.LOST || it.status == BidStatus.FAILED }
            ?.map { it.bid }
            ?: emptyList()
    }

    /**
     * Get all bids for an auction
     */
    fun getAllBids(auctionId: String): List<Bid> {
        return auctionBids[auctionId]?.map { it.bid } ?: emptyList()
    }

    /**
     * Get a specific bid
     */
    fun getBid(auctionId: String, bidId: String): Bid? {
        return auctionBids[auctionId]?.find { it.bid.id == bidId }?.bid
    }

    /**
     * Process all win/loss notifications for an auction
     */
    fun processAuctionWinLoss(auctionId: String, winLossTracker: WinLossTracker) {
        val bids = auctionBids[auctionId] ?: return

        bids.forEach { entry ->
            val bid = entry.bid
            val baseData = mutableMapOf<String, Any>(
                "bidId" to bid.id,
                "networkName" to bid.adNetwork.networkName,
                "bidPrice" to (bid.price?.toDouble() ?: 0.0),
                "rank" to bid.rank,
                "rawBid" to bid.rawJson
            )

            when (entry.status) {
                BidStatus.WON, BidStatus.LOADED -> {
                    val winPrice = entry.actualWinPrice ?: bid.price?.toDouble() ?: 0.0
                    winLossTracker.sendWin(auctionId, winPrice, baseData)
                }

                BidStatus.LOST, BidStatus.FAILED -> {
                    val lossReason = entry.lossReason ?: LossReason.LostToHigherBid
                    winLossTracker.sendLoss(
                        auctionId,
                        lossReason,
                        baseData + ("lossReasonCode" to lossReason.code)
                    )
                }

                BidStatus.PENDING -> {
                    // Auction incomplete, mark as lost to higher bid (default)
                    entry.status = BidStatus.LOST
                    entry.lossReason = LossReason.LostToHigherBid
                    winLossTracker.sendLoss(
                        auctionId,
                        LossReason.LostToHigherBid,
                        baseData + ("lossReasonCode" to LossReason.LostToHigherBid.code)
                    )
                }
            }
        }
    }

    /**
     * Clear auction data (call after processing win/loss)
     */
    fun clearAuction(auctionId: String) {
        auctionBids.remove(auctionId)
        auctionStatus.remove(auctionId)
        CloudXLogger.d(tag, "Cleared auction data for $auctionId")
    }

    /**
     * Clear all auction data
     */
    fun clear() {
        auctionBids.clear()
        auctionStatus.clear()
        CloudXLogger.d(tag, "Cleared all auction data")
    }
}
