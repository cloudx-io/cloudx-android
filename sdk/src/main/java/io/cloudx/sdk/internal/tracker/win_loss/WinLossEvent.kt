package io.cloudx.sdk.internal.tracker.win_loss

/**
 * Win/Loss event types that can be triggered during the ad lifecycle.
 * Each event corresponds to a specific point in the ad serving flow.
 */
internal enum class WinLossEvent(val eventKey: String) {
    /**
     * Triggered when an ad starts loading
     */
    LOAD_START("onLoadStart"),

    /**
     * Triggered when an ad successfully loads
     */
    LOAD_SUCCESS("onLoadSuccess"),

    /**
     * Triggered when an ad fails to load
     */
    LOAD_FAIL("onLoadFail"),

    /**
     * Triggered when an ad successfully renders/shows
     */
    RENDER_SUCCESS("onRenderSuccess"),

    /**
     * Triggered when an ad loses (general loss event)
     */
    LOSS("onLoss")
}