package io.cloudx.sdk.internal.ads.banner

import VisibilityAwareRefreshClock
import android.app.Activity
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.ads.banner.components.BannerAdLoader
import io.cloudx.sdk.internal.ads.banner.components.BannerLoadOutcome
import io.cloudx.sdk.internal.ads.banner.components.EffectiveVisibilityGate
import io.cloudx.sdk.internal.ads.banner.components.SwappingBannerPresenter
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.decorate
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal interface BannerManager : Destroyable {

    var listener: CloudXAdViewListener?
}

internal fun BannerManager(
    activity: Activity,
    placementId: String,
    placementName: String,
    adViewContainer: CloudXAdViewAdapterContainer,
    bannerVisibility: StateFlow<Boolean>,
    refreshSeconds: Int,
    adType: AdType,
    bidFactories: Map<AdNetwork, CloudXAdViewAdapterFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    bidAdLoadTimeoutMillis: Long,
    miscParams: BannerFactoryMiscParams,
    bidApi: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTrackerNew: MetricsTrackerNew,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    accountId: String,
    appKey: String
): BannerManager {

    val bidRequestProvider = BidRequestProvider(
        bidRequestExtrasProviders
    )

    val bidSource =
        BidBannerSource(
            activity,
            adViewContainer,
            refreshSeconds,
            bidFactories,
            placementId,
            placementName,
            adType,
            bidApi,
            cdpApi,
            bidRequestProvider,
            eventTracker,
            metricsTrackerNew,
            miscParams,
            0,
            accountId,
            appKey
        )

    val loader = BannerAdLoader(
        bidAdSource = bidSource,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        placementName = placementName,
        placementId = placementId
    )

    return BannerManagerImpl(
        placementId = placementId,
        placementName = placementName,
        bannerVisibility = bannerVisibility,
        refreshSeconds = refreshSeconds,
        connectionStatusService = connectionStatusService,
        appLifecycleService = appLifecycleService,
        metricsTrackerNew = metricsTrackerNew,
        loader = loader
    )
}

/**
 * BannerManagerImpl implements the MVP Banner Refresh specification.
 *
 * MVP Requirements implemented:
 * - Single-flight requests (one active request per placement)
 * - Wall-clock refresh timing that continues while hidden
 * - Queue exactly one request when hidden, emit when visible
 * - Complete in-flight requests even if banner becomes hidden
 * - Prefetch successful loads while hidden, show when visible
 * - No banner-level retries (network retries handled at lower layer)
 * - Proper resource cleanup on destroy
 *
 * Architecture: Divided into 4 specialized components for maintainability:
 * 1. VisibilityAwareRefreshClock - Handles wall-clock timing + visibility-aware queuing
 * 2. EffectiveVisibilityGate - Combines banner + app visibility into single effective state
 * 3. SwappingBannerPresenter - Manages banner display lifecycle and events
 * 4. BannerManagerImpl - Orchestrates the above components + handles business logic
 */
private class BannerManagerImpl(
    private val placementId: String,
    private val placementName: String,
    private val bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    private val connectionStatusService: ConnectionStatusService,
    private val appLifecycleService: AppLifecycleService,
    private val metricsTrackerNew: MetricsTrackerNew,
    private val loader: BannerAdLoader,
) : BannerManager {

    private val TAG = "BannerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override var listener: CloudXAdViewListener? = null
        set(v) {
            field = v?.decorate()
        }

    // MVP: Cache successful loads when hidden, display when visible
    private var prefetched: BannerAdapterDelegate? = null

    // === COMPONENT ARCHITECTURE ===

    // 1. TIMING: Wall-clock refresh that continues while hidden (MVP requirement)
    private val clock = VisibilityAwareRefreshClock(
        intervalMs = (refreshSeconds.coerceAtLeast(1) * 1000L),
        scope = scope
    )

    // 2. VISIBILITY: Combines banner visibility + app lifecycle (MVP requirement)
    private val appForeground: StateFlow<Boolean> = appLifecycleService.isResumed
    private val gate = EffectiveVisibilityGate(bannerVisibility, appForeground, scope)

    // 3. PRESENTATION: Handles banner display/hide/events lifecycle
    private val presenter = SwappingBannerPresenter(
        placementId = placementId,
        placementName = placementName,
        listener = { listener },
        scope = scope
    )

    private var tickJob: Job? = null
    private var visJob: Job? = null
    private var reqJob: Job? = null

    init {
        CloudXLogger.i(
            TAG,
            placementName,
            placementId,
            "BannerManager init (refresh=${refreshSeconds}s)"
        )
        start()
    }

    private fun start() {
        // === MVP: VISIBILITY HANDLING ===
        // React to effective visibility changes (banner visible + app foreground)
        visJob?.cancel()
        visJob = scope.launch {
            gate.effective.collect { visible ->
                // MVP: Inform clock of visibility changes for queuing logic
                clock.setVisible(visible)

                // MVP: Show prefetched banners immediately when becoming visible
                if (visible) {
                    prefetched?.let { presenter.show(it); prefetched = null }
                }
            }
        }

        // MVP: Start wall-clock timing (continues even while hidden)
        clock.start()

        // === MVP: REQUEST TRIGGERING ===
        // Listen to clock ticks and launch single-flight requests
        tickJob?.cancel()
        tickJob = scope.launch {
            clock.ticks.collect {
                launchRequest() // MVP: Single-flight - only one active at a time
            }
        }
    }

    private fun launchRequest() {
        // MVP: Mark request as in-flight to prevent stacking
        clock.markRequestStarted()
        reqJob?.cancel() // Cancel any previous request (should not happen due to single-flight)
        reqJob = scope.launch {
            metricsTrackerNew.trackMethodCall(MetricsType.Method.BannerRefresh)

            // MVP: Wait for network connection before proceeding
            try {
                connectionStatusService.awaitConnection()
            } catch (_: CancellationException) {
                clock.markRequestFinished() // MVP: Always mark finished to restart timer
                return@launch
            }

            // MVP: Single load attempt (no banner-level retries)
            when (val outcome = loader.loadOnce()) {
                is BannerLoadOutcome.Success -> {
                    // MVP: Show immediately if visible, prefetch if hidden
                    if (gate.effective.value) {
                        presenter.show(outcome.banner)
                    } else {
                        // MVP: Complete request even if hidden - cache for later display
                        prefetched?.destroy(); prefetched = outcome.banner
                        CloudXLogger.d(TAG, placementName, placementId, "Prefetched while hidden")
                    }
                }

                BannerLoadOutcome.NoFill ->
                    // MVP: Emit error and wait for next interval (no banner retry)
                    listener?.onAdLoadFailed(CloudXAdError("No fill", CLXErrorCode.NO_FILL.code))

                BannerLoadOutcome.TransientFailure ->
                    // MVP: Network/5xx errors - emit error, continue refresh cycle
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Temporary error",
                            CLXErrorCode.SERVER_ERROR.code
                        )
                    )

                BannerLoadOutcome.PermanentFailure -> {
                    // MVP: 4xx client errors - stop permanently
                    stopPermanently("Permanent error", CLXErrorCode.CLIENT_ERROR.code)
                    return@launch
                }

                BannerLoadOutcome.TrafficControl ->
                    // MVP: Ads disabled - emit error, continue refresh cycle
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Ads disabled",
                            CLXErrorCode.ADS_DISABLED.code
                        )
                    )
            }

            // MVP: Mark request finished to restart timer and avoid stacking
            clock.markRequestFinished()
        }
    }

    private fun stopPermanently(userMessage: String, code: Int) {
        // Best-effort surface the fatal error before teardown
        listener?.onAdLoadFailed(CloudXAdError(userMessage, code))
        CloudXLogger.e(
            TAG,
            placementName,
            placementId,
            "Permanent failure → stopping: $userMessage"
        )
        destroy()
    }

    private var isDestroyed = false
    override fun destroy() {
        if (isDestroyed) return
        isDestroyed = true

        // MVP: Cancel all timers and requests
        scope.cancel()
        tickJob?.cancel()
        visJob?.cancel()
        reqJob?.cancel()
        clock.stop()

        // MVP: Clear prefetched creatives
        prefetched?.destroy()
        presenter.destroy()
        prefetched = null

        CloudXLogger.d(TAG, placementName, placementId, "Destroyed")
    }
}

