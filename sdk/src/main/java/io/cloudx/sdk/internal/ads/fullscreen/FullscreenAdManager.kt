package io.cloudx.sdk.internal.ads.fullscreen

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXAdRevenueListener
import io.cloudx.sdk.CloudXFullscreenAd
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.AdLoader
import io.cloudx.sdk.internal.tracker.SessionMetricsTracker
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
    private val adType: AdType,
    private val adLoader: AdLoader<Delegate>,
    // Listens to the current ad events and returns FullscreenAdEvent if similar.
    private val tryHandleCurrentEvent: DelegateEvent.(cloudXAd: CloudXAd) -> FullscreenAdEvent?,
) : CloudXFullscreenAd<CloudXAdListener> {

    // Core components
    private val logger = CXLogger.forPlacement(tag, placementName)
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
            logger.w("Ad already loading")
            return
        }

        if (lastLoadedAd != null) {
            logger.w("Ad already loaded")
            return
        }

        logger.i("Starting ad load")
        lastLoadJob = scope.launch {
            try {
                adLoader.load().fold(
                    onSuccess = { loadedAd ->
                        logger.i("Ad loaded successfully")
                        lastLoadedAd = loadedAd
                        listener?.onAdLoaded(loadedAd)
                    },
                    onFailure = { error ->
                        logger.e("Ad load failed: ${error.message}", error.cause)
                        listener?.onAdLoadFailed(error)
                    }
                )
            } catch (cancellation: CancellationException) {
                logger.d("Ad load cancelled")
                listener?.onAdLoadFailed(cancellation.toCloudXError())
                throw cancellation
            } catch (exception: Exception) {
                logger.e("Unexpected error during ad load", exception)
                listener?.onAdLoadFailed(exception.toCloudXError())
            }
        }
    }

    override val isAdReady get() = lastLoadedAd != null && lastShowJob?.isActive != true

    // Ad showing methods
    override fun show() {
        val ad = lastLoadedAd
        if (ad == null) {
            logger.w("Ad not loaded")
            return
        }

        lastShowJob?.let { job ->
            if (job.isActive) {
                logger.w("Ad already displaying")
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

            try {
                ad.show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Unexpected error during ad show", e)
                isError = true
                hideOrErrorEvent.value = true
                listener?.onAdDisplayFailed(e.toCloudXError())
            }

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
                            SessionMetricsTracker.recordImpression(placementName, adType)
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
        SessionMetricsTracker.resetPlacement(placementName)
    }
}

internal enum class FullscreenAdEvent {
    Show, Click, Hide
}
