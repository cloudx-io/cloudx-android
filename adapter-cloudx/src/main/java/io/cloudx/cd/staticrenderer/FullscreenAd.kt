package io.cloudx.cd.staticrenderer

import io.cloudx.sdk.Destroyable

/**
 * Common public API for all fullscreen ad renderers (mraid, vast, static etc) <br>

 * The use-case scenario is: <br>
 * 1. Create an instance of [FullscreenAd] <br>
 * 2. call [load] <br>
 * 3. wait for [FullscreenAd.Listener.onLoad] callback <br>
 * 4. call [show] function <br>
 *
 * CLEANING UP RESOURCES:<br>
 *
 * In case of getting [Listener.onHide], [Listener.onLoadError] or [Listener.onShowError] callback events, or simply when [Activity] is destroyed, in order to prevent memory leaks make sure to call [destroy] method <br>
 *
 * CAVEATS: <br>
 *
 * Current implementations of [FullscreenAd] interface do not support multiple [load] and [show] calls: results may be unpredictable <br>
 */
interface FullscreenAd<T : FullscreenAd.Listener> : Destroyable {

    fun load()
    fun show()

    // Todo.
    //  1. Implement isAdLoaded, isDisplaying observable properties.
    //  2. Discuss if we need to  make load() and show() methods suspendable?
    //  3. Consider replacing listener with StateFlow and/or SharedFlow?

    var listener: T?

    interface Listener {

        fun onLoad()
        fun onLoadError()
        fun onShow()
        fun onShowError()
        fun onImpression()
        fun onComplete()
        fun onClick()
        fun onHide()
    }
}