package io.cloudx.sdk.internal.ads.fullscreen

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.CloudXFullscreenAd
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.AdLoader
import io.cloudx.sdk.internal.util.utcNowEpochMillis
import io.cloudx.sdk.toCloudXError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class FullscreenAdManager<
        Delegate : FullscreenAdAdapterDelegate<DelegateEvent>,
        DelegateEvent>(
    private val adLoader: AdLoader<Delegate>,
    private val placementType: AdType,
    // Listens to the current ad events and returns FullscreenAdEvent if similar.
    private val tryHandleCurrentEvent: DelegateEvent.(cloudXAd: CloudXAd) -> FullscreenAdEvent?,
) : CloudXFullscreenAd<CloudXAdListener> {

    // Core components
    private val scope = CoroutineScope(Dispatchers.Main)

    // Loading state
    private var lastLoadJob: Job? = null
    private var lastLoadedAd: FullscreenAdAdapterDelegate<DelegateEvent>? = null

    // Showing state
    private var lastShowJob: Job? = null
    private var lastShowJobStartedTimeMillis: Long = -1

    // Listener management
    override var listener: CloudXAdListener? = null

    // Ad loading methods
    override fun load() {
        if (lastLoadJob?.isActive == true || lastLoadedAd != null) return
        lastLoadJob = scope.launch {
            adLoader.load().fold(
                onSuccess = {
                    lastLoadedAd = it
                    listener?.onAdLoaded(it)
                },
                onFailure = {
                    listener?.onAdLoadFailed(it)
                }
            )
        }
    }

    override val isAdLoaded get() = lastLoadedAd != null

    // Ad showing methods
    override fun show() {
        CXLogger.i(
            "CloudX${if (placementType == AdType.Interstitial) "Interstitial" else "Rewarded"}",
            "show() was called"
        )

        lastShowJob?.let { job ->
            // If no adHidden even has been received within the allotted time, then we force an adHidden event and allow the display of a new ad
            if (job.isActive) {
                val timeToWaitForHideEventMillis = 90 * 1000
                if (utcNowEpochMillis() <= (lastShowJobStartedTimeMillis + timeToWaitForHideEventMillis)) {
                    listener?.onAdDisplayFailed(CloudXErrorCode.AD_ALREADY_DISPLAYED.toCloudXError())
                    return
                } else {
                    job.cancel("No adHidden or adError event received. Cancelling job")
                    lastLoadedAd?.let {
                        listener?.onAdHidden(it)
                    }
                    lastLoadedAd = null
                }
            }
        }

        lastShowJob = scope.launch {
            lastShowJobStartedTimeMillis = utcNowEpochMillis()

            val ad = lastLoadedAd
            if (ad == null) {
                listener?.onAdDisplayFailed(CloudXErrorCode.AD_NOT_READY.toCloudXError())
                return@launch
            }

            try {
                show(ad)
            } finally {
                ad.destroy()
                lastLoadedAd = null
            }
        }
    }

    private suspend fun show(ad: FullscreenAdAdapterDelegate<DelegateEvent>) =
        coroutineScope {
            // And the correct behaviour of "terminal hide event"
            // should be treated somewhere else anyways. By the way:
            // TODO. implement suspend SuspendableInterstitial.show()?
            // In case there's error event and no hide event after. I need to send hide event anyway.
            var isHideEventSent = false
            var isError = false
            // Unfortunately, Flow.flattenMerge and flatMapMerge are in FlowPreview state...
            val hideOrErrorEvent = MutableStateFlow(false)

            val adLifecycleJob = trackAdLifecycle(
                ad,
                onHide = {
                    isHideEventSent = true
                    hideOrErrorEvent.value = true
                },
                onLastError = {
                    isError = true
                    hideOrErrorEvent.value = true
                }
            )

            ad.show()

            hideOrErrorEvent.first { it }

            adLifecycleJob.cancel()
            if (isError) {
                return@coroutineScope false
            }

            if (!isHideEventSent) listener?.onAdHidden(ad)

            return@coroutineScope true
        }

    // Ad lifecycle management methods
    private fun CoroutineScope.trackAdLifecycle(
        ad: FullscreenAdAdapterDelegate<DelegateEvent>,
        onHide: () -> Unit,
        onLastError: () -> Unit,
    ) =
        launch {
            launch {
                ad.event.collect {
                    when (it.tryHandleCurrentEvent(ad)) {
                        FullscreenAdEvent.Show -> {
                            listener?.onAdDisplayed(ad)
                        }

                        FullscreenAdEvent.Click -> {
                            listener?.onAdClicked(ad)
                        }
                        // TODO. Check if adapters send important events (reward, complete) only before "hide" event.
                        //  They might be lost after job cancellation otherwise.
                        //  Fix ad network's adapter then. I guess.
                        //  Make sure "hide" is the last event in sequence.
                        FullscreenAdEvent.Hide -> {
                            listener?.onAdHidden(ad)
                            onHide()
                        }

                        else -> {}
                    }
                }
            }
            launch {
                val error = ad.lastErrorEvent.first { it != null }
                error?.let { listener?.onAdDisplayFailed(error) }
                onLastError()
            }
        }

    // Cleanup methods
    override fun destroy() {
        scope.cancel()
    }
}

internal enum class FullscreenAdEvent {
    Show, Click, Hide
}
