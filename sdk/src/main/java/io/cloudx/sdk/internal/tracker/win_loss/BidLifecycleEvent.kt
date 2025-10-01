package io.cloudx.sdk.internal.tracker.win_loss

/**
 * Bid lifecycle event types that can be triggered during the ad lifecycle.
 * Each event corresponds to a specific point in the ad serving flow.
 */
internal enum class BidLifecycleEvent(
    val notificationType: String
) {

    /**
     * Triggered when an ad successfully loads
     */
    LOAD_SUCCESS("loadSuccess"),

    /**
     * Triggered when an ad successfully renders/shows
     */
    RENDER_SUCCESS("renderSuccess"),

    /**
     * Triggered when an ad loses (general loss event)
     */
    LOSS("loss")
}