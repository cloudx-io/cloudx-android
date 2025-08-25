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
     * Ad was not loaded. An error happened. You can check details using the [cloudXAdError] object
     */
    fun onAdLoadFailed(cloudXAdError: CloudXAdError)

    /**
     * Ad was not shown. An error happened. You can check details using the [cloudXAdError] object
     */
    fun onAdDisplayFailed(cloudXAdError: CloudXAdError)
}