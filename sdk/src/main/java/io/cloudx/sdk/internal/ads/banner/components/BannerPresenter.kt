package io.cloudx.sdk.internal.ads.banner.components

import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.banner.BannerAdapterDelegate
import io.cloudx.sdk.internal.ads.banner.BannerAdapterDelegateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal interface BannerPresenter {
    fun show(banner: BannerAdapterDelegate)
    fun destroy()
    fun hideCurrent() // optional hook if needed
}

internal class DefaultBannerPresenter(
    private val placementId: String,
    private val placementName: String,
    private val listener: () -> CloudXAdViewListener?,
    private val scope: CoroutineScope
) : BannerPresenter {

    private val TAG = "BannerPresenter"
    private var eventsJob: Job? = null
    private var current: BannerAdapterDelegate? = null

    override fun show(banner: BannerAdapterDelegate) {
        // teardown old
        eventsJob?.cancel()
        current?.let {
            listener()?.onAdHidden(it)
            it.destroy()
        }
        current = banner

        CloudXLogger.d(TAG, placementName, placementId, "Displaying banner")
        listener()?.onAdDisplayed(banner)

        // wire minimal events
        eventsJob = scope.launch {
            // clicks
            launch {
                banner.event.collect { ev ->
                    if (ev == BannerAdapterDelegateEvent.Click) {
                        listener()?.onAdClicked(banner)
                    }
                }
            }
            // errors
            launch {
                val err = banner.lastErrorEvent.first { it != null }
                CloudXLogger.w(TAG, placementName, placementId, "Banner error: $err → destroy current")
                current?.let { listener()?.onAdHidden(it) }
                current?.destroy()
                current = null
                // cadence remains controlled by clock
            }
        }
    }

    override fun hideCurrent() {
        current?.let { listener()?.onAdHidden(it) }
    }

    override fun destroy() {
        eventsJob?.cancel()
        current?.destroy()
        current = null
    }
}
