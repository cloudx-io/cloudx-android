package io.cloudx.sdk.internal.imp_tracker.win_loss

/**
 * Represents the reason why a bid was lost in an auction.
 *
 * This enum is used to categorize the reasons for losing a bid, which can help in debugging and
 * understanding auction dynamics.
 *
 * Note! The commented out values are not needed for the SDK implementation and are left for reference.
 *
 * @property code An integer code representing the loss reason.
 * @property description A human-readable description of the loss reason.
 */
enum class LossReason(val code: Int, val description: String) {
//  Unknown(0, "Unknown"),                                      // ❌ Not Needed
    TechnicalError(1, "Technical error"),       // ✅ if crash happens, we assign this reason.
//  Timeout(2, "Timeout"),                                      // ❌ SSP side
//  BelowBidFloor(3, "Bid below bidfloor"),                     // ❌ SSP side
    LostToHigherBid(4, "Lost to higher bid"),   // ✅ when the ad is not selected because another bid has a higher RANK.
//  CreativeRejected(5, "Creative rejected")                    // ❌ SSP side
}
