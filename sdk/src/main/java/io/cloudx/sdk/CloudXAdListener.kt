package io.cloudx.sdk

interface CloudXAdListener {
    /**
     * Ad was loaded.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onAdLoaded(cloudXAd: CloudXAd)

    /**
     * Ad was displayed.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onAdDisplayed(cloudXAd: CloudXAd)

    /**
     * Ad was hidden.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onAdHidden(cloudXAd: CloudXAd)

    /**
     * Ad was clicked.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onAdClicked(cloudXAd: CloudXAd)

    /**
     * Ad was not loaded. An error happened. You can check details using the [cloudXError] object
     */
    fun onAdLoadFailed(cloudXError: CloudXError)

    /**
     * Ad was not shown. An error happened. You can check details using the [cloudXError] object
     */
    fun onAdDisplayFailed(cloudXError: CloudXError)
}