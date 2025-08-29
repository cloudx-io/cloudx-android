package io.cloudx.sdk

import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adToDisplayInfo
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.common.utcNowEpochMillis
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.baseinterstitial.CacheableAd
import io.cloudx.sdk.internal.core.ad.baseinterstitial.CachedAdRepository
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSource
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableBaseFullscreenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Base fullscreen ad interface
 */
interface CloudXFullscreenAd : CloudXAdToDisplayInfoApi, Destroyable {

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

// TODO. Yeah, more generics, classes, interfaces...
internal class CloudXFullscreenAdImpl<
        SuspendableFullscreenAd : SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>,
        SuspendableFullscreenAdEvent,
        PublisherListener : CloudXAdListener,
        >(
    bidAdSource: BidAdSource<SuspendableFullscreenAd>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    cacheSize: Int,
    private val placementType: AdType,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    private val listener: PublisherListener,
    // TODO. Ahaha. stop it, please.
    // Listens to the current ad events and returns BaseSuspendableFullscreenAdEvent if similar.
    private val tryHandleCurrentEvent: SuspendableFullscreenAdEvent.(cloudXAd: CloudXAd) -> BaseSuspendableFullscreenAdEvent?
) : CloudXFullscreenAd {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val cachedAdRepository = CachedAdRepository(
        bidAdSource,
        cacheSize,
        preCachedAdLifetimeMinutes = 30,
        bidMaxBackOffTimeMillis = bidMaxBackOffTimeMillis,
        bidLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        createCacheableAd = { CacheableFullscreenAd(it) },
        placementType = placementType,
        connectionStatusService = connectionStatusService,
        appLifecycleService = appLifecycleService
    )

    override val adToDisplayInfo: CloudXAdToDisplayInfoApi.Info?
        get() = cachedAdRepository.adToDisplayInfo

    private var isAdReadyListenerJob: Job? = null

    override fun setIsAdLoadedListener(listener: CloudXIsAdLoadedListener?) {
        isAdReadyListenerJob?.cancel()
        listener ?: return

        isAdReadyListenerJob = cachedAdRepository.hasAds
            .onEach { hasAds ->
                listener.onIsAdLoadedStatusChanged(hasAds)
            }
            .launchIn(scope)
    }

    // TODO. Duplicate of Interstitial + errors
    override fun load() {
        if (lastLoadJob?.isActive == true) return

        lastLoadJob = scope.launch {
            val hasAds = withTimeoutOrNull(2000) {
                cachedAdRepository.hasAds.first { it }
                true
            }

            if (hasAds == true) {
                val topAdMetaData = cachedAdRepository.topAdMetaData
                listener.onAdLoaded(CloudXAd(topAdMetaData?.adNetwork))
            } else {
                listener.onAdLoadFailed(CloudXAdError(description = "No ads loaded yet", errorCode = CloudXErrorCodes.HYPOTHETICAL_NO_ADS_LOADED_YET))
            }
        }
    }

    override val isAdLoaded get() = cachedAdRepository.hasAds.value

    private var lastLoadJob: Job? = null

    override fun show() {
        CloudXLogger.info(
            "CloudX${if (placementType == AdType.Interstitial) "Interstitial" else "Rewarded"}",
            "show() was called"
        )

        lastShowJob?.let { job ->
            // If no adHidden even has been received within the allotted time, then we force an adHidden event and allow the display of a new ad
            if (job.isActive) {
                val timeToWaitForHideEventMillis = 90 * 1000
                if (utcNowEpochMillis() <= (lastShowJobStartedTimeMillis + timeToWaitForHideEventMillis)) {
                    listener.onAdDisplayFailed(CloudXAdError(description = "Ad is already displaying", errorCode = CloudXErrorCodes.HYPOTHETICAL_AD_ALREADY_DISPLAYING))
                    return
                } else {
                    job.cancel("No adHidden or adError event received. Cancelling job")
                    lastShownAd?.let {
                        listener.onAdHidden(CloudXAd(it.adNetwork))
                    }
                }
            }
        }

        lastShowJob = scope.launch {
            lastShowJobStartedTimeMillis = utcNowEpochMillis()

            val ad = popAdAndSetLastShown()
            if (ad == null) {
                listener.onAdDisplayFailed(CloudXAdError(description = "No ads loaded yet", errorCode = CloudXErrorCodes.HYPOTHETICAL_NO_ADS_LOADED_YET))
                return@launch
            }

            // TODO refactor this part of code. It's hard to understand.
            showWithRetry(ad)
        }
    }

    /**
     * Tries to show the ad, but in case some error occurs, then it will retry for up to 3 times.
     */
    private suspend fun showWithRetry(ad: SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>) {
        val showRetryDelayMillis = 100L
        val showRetryCountMax = 3
        var showRetryCount = 0
        var showRetryAd: SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>? = ad
        while (showRetryAd != null) {
            val shown: Boolean
            try {
                shown = show(showRetryAd) == true
            } finally {
                showRetryAd.destroy()
            }

            showRetryAd =
                if (shown || ++showRetryCount >= showRetryCountMax) null else popAdAndSetLastShown()

            if (!shown) {
                CloudXLogger.info(
                    "CloudX ${if (placementType == AdType.Interstitial) "Interstitial" else "Rewarded"}",
                    "Ad was not shown, retry show ${showRetryAd != null}, retry count $showRetryCount"
                )

                // we delay to give resources a chance to clean up
                delay(showRetryDelayMillis)
            }
        }
    }

    private fun popAdAndSetLastShown(): SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>? {
        return cachedAdRepository.popAd().apply { lastShownAd = this }
    }

    private var lastShowJob: Job? = null
    private var lastShowJobStartedTimeMillis: Long = -1
    private var lastShownAd: SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>? = null

    private suspend fun show(ad: SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>) =
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

            if (!isHideEventSent) listener.onAdHidden(CloudXAd(ad.adNetwork))

            return@coroutineScope true
        }

    private fun CoroutineScope.trackAdLifecycle(
        ad: SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>,
        onHide: () -> Unit,
        onLastError: () -> Unit,
    ) =
        launch {
            launch {
                val network = ad.adNetwork
                ad.event.collect {
                    val cloudXAd = CloudXAd(network)
                    when (it.tryHandleCurrentEvent(cloudXAd)) {
                        BaseSuspendableFullscreenAdEvent.Show -> {
                            listener.onAdDisplayed(cloudXAd)
                        }

                        BaseSuspendableFullscreenAdEvent.Click -> {
                            listener.onAdClicked(cloudXAd)
                        }
                        // TODO. Check if adapters send important events (reward, complete) only before "hide" event.
                        //  They might be lost after job cancellation otherwise.
                        //  Fix ad network's adapter then. I guess.
                        //  Make sure "hide" is the last event in sequence.
                        BaseSuspendableFullscreenAdEvent.Hide -> {
                            listener.onAdHidden(cloudXAd)
                            onHide()
                        }

                        else -> {}
                    }
                }
            }
            launch {
                ad.lastErrorEvent.first { it != null }
                onLastError()
            }
        }

    override fun destroy() {
        scope.cancel()
        cachedAdRepository.destroy()
    }
}

private class CacheableFullscreenAd<SuspendableFullscreenAdEvent>(
    suspendableFullscreenAd: SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent>,
) :
    CacheableAd,
    SuspendableBaseFullscreenAd<SuspendableFullscreenAdEvent> by suspendableFullscreenAd

// TODO. Ahaha, stop it, please.
internal enum class BaseSuspendableFullscreenAdEvent {

    Show, Click, Hide
}