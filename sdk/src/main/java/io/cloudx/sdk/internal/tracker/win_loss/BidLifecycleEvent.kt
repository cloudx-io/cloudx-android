package io.cloudx.sdk.internal.tracker.win_loss

/**
 * Bid lifecycle event types that can be triggered during the ad lifecycle.
 * Each event corresponds to a specific point in the ad serving flow.
 */
internal enum class BidLifecycleEvent(
    val notificationType: String,
    val urlType: String
) {

    /**
     * When a bid is received (general bid event)
     */
    BID_RECEIVED("", "lurl"),

    /**
     * When an ad successfully loads
     */
    LOAD_SUCCESS("loadSuccess", "nurl"),

    /**
     * When an ad successfully renders/shows
     */
    RENDER_SUCCESS("renderSuccess", "burl"),

    /**
     * When an ad loses (general loss event)
     */
    LOSS("loss", "lurl")
}