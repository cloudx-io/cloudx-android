package io.cloudx.sdk

interface CloudXAdViewListener : CloudXAdListener {
    /**
     * User manually expanded the ad banner.
     */
    fun onAdExpanded(placementName: String)

    /**
     * User manually closed the ad banner. It is the responsibility of the publisher to reload it again.
     */
    fun onAdCollapsed(placementName: String)
}