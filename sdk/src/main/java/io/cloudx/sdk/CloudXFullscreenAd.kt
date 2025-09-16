package io.cloudx.sdk

/**
 * Fullscreen ad interface
 */
interface CloudXFullscreenAd<T: CloudXAdListener> : Destroyable {
    var listener: T?

    /**
     * Tells about current ad load status.
     */
    val isAdLoaded: Boolean

    /**
     * Sets [CloudXIsAdLoadedListener] which listens to "ad loaded" state changes.
     */
    fun setIsAdLoadedListener(listener: CloudXIsAdLoadedListener?)

    /**
     * Loads ad; if ad is loaded, successful listener's [onAdLoadSuccess()][CloudXAdListener.onAdLoaded] will be invoked; [onAdLoadFailed()][CloudXAdListener.onAdLoadFailed] otherwise.
     */
    fun load()

    /**
     * Shows ad; if show is successful listener's [onAdShowSuccess()][CloudXAdListener.onAdDisplayed] will be invoked; [onAdShowFailed()][CloudXAdListener.onAdDisplayFailed] otherwise;
     * Ad fail can happen when ad is not [loaded][load] yet or due to internal ad display error.
     */
    fun show()
}
