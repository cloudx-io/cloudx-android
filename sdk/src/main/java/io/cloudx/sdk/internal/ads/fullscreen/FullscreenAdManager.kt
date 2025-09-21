package io.cloudx.sdk.internal.ads.fullscreen

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXAdRevenueListener
import io.cloudx.sdk.CloudXFullscreenAd
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.AdLoader
import io.cloudx.sdk.internal.util.ThreadUtils
import io.cloudx.sdk.toCloudXError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class FullscreenAdManager<
        Delegate : FullscreenAdAdapterDelegate<DelegateEvent>,
        DelegateEvent>(
    private val tag: String,
    private val placementName: String,
    private val placementId: String,
    private val adLoader: AdLoader<Delegate>,
    // Listens to the current ad events and returns FullscreenAdEvent if similar.
    private val tryHandleCurrentEvent: DelegateEvent.(cloudXAd: CloudXAd) -> FullscreenAdEvent?,
) : CloudXFullscreenAd<CloudXAdListener> {

    // Core components
    private val scope = ThreadUtils.createMainScope(tag)

    // Loading state
    private var lastLoadJob: Job? = null
    private var lastLoadedAd: FullscreenAdAdapterDelegate<DelegateEvent>? = null

    // Showing state
    private var lastShowJob: Job? = null

    // Listener management
    override var listener: CloudXAdListener? = null

    override var revenueListener: CloudXAdRevenueListener? = null

    // Ad loading methods
    override fun load() {
        if (lastLoadJob?.isActive == true) {
            CXLogger.w(tag, placementName, "Ad already loading")
            return
        }

        if (lastLoadedAd != null) {
            CXLogger.w(tag, placementName, "Ad already loaded")
            return
        }

        CXLogger.i(tag, placementName, "Starting ad load")
        lastLoadJob = scope.launch {
            try {
                adLoader.load().fold(
                    onSuccess = { loadedAd ->
                        CXLogger.i(tag, placementName, "Ad loaded successfully")
                        lastLoadedAd = loadedAd
                        listener?.onAdLoaded(loadedAd)
                    },
                    onFailure = { error ->
                        CXLogger.e(
                            tag,
                            placementName,
                            "Ad load failed: ${error.message}",
                            error.cause
                        )
                        listener?.onAdLoadFailed(error)
                    }
                )
            } catch (cancellation: CancellationException) {
                CXLogger.d(tag, placementName, "Ad load cancelled")
                listener?.onAdLoadFailed(cancellation.toCloudXError())
                throw cancellation
            } catch (exception: Exception) {
                CXLogger.e(
                    tag,
                    placementName,
                    "Unexpected error during ad load",
                    exception
                )
                listener?.onAdLoadFailed(exception.toCloudXError())
            }
        }
    }

    override val isAdReady get() = lastLoadedAd != null && lastShowJob?.isActive != true

    // Ad showing methods
    override fun show() {
        val ad = lastLoadedAd
        if (ad == null) {
            CXLogger.w(tag, placementName, "Ad not loaded")
            return
        }

        lastShowJob?.let { job ->
            if (job.isActive) {
                CXLogger.w(tag, placementName, "Ad already displaying")
                return
            }
        }

        lastShowJob = scope.launch {
            try {
                show(ad)
            } finally {
                destroyLastLoadedAd()
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
    private fun destroyLastLoadedAd() {
        lastLoadedAd?.destroy()
        lastLoadedAd = null
    }

    override fun destroy() {
        destroyLastLoadedAd()
        scope.cancel()
    }
}

internal enum class FullscreenAdEvent {
    Show, Click, Hide
}
