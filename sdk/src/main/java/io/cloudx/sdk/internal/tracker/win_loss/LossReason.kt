package io.cloudx.sdk.internal.tracker.win_loss

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
    BID_WON(0, "Bid Won"),
    INTERNAL_ERROR(1, "Internal Error"),
    LOST_TO_HIGHER_BID(102, "Lost to Higher Bid"),
}
