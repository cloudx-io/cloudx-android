package io.cloudx.sdk.internal.tracker.win_loss

/**
 * Bid lifecycle event types that can be triggered during the ad lifecycle.
 * Each event corresponds to a specific point in the ad serving flow.
 */
internal enum class BidLifecycleEvent(
    val eventKey: String,
    val notificationType: String
    ) {
    /**
     * Triggered when an ad starts loading
     */
    LOAD_START("onLoadStart", "loadStart"),

    /**
     * Triggered when an ad successfully loads
     */
    LOAD_SUCCESS("onLoadSuccess", "loadSuccess"),

    /**
     * Triggered when an ad fails to load
     */
    LOAD_FAIL("onLoadFail", "loadFail"),

    /**
     * Triggered when an ad successfully renders/shows
     */
    RENDER_SUCCESS("onRenderSuccess", "renderSuccess"),

    /**
     * Triggered when an ad loses (general loss event)
     */
    LOSS("onLoss", "loss")
}