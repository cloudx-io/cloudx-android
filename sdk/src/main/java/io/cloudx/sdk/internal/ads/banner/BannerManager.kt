package io.cloudx.sdk.internal.ads.banner

import VisibilityAwareRefreshClock
import android.app.Activity
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.ads.banner.components.BannerAdLoader
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
import io.cloudx.sdk.internal.isNoFill
import io.cloudx.sdk.internal.isTrafficControl
import io.cloudx.sdk.internal.isTransientFailure
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
 * - Visibility-driven refresh timing that pauses while hidden
 * - No hidden queuing; requests are driven only while visible
 * - Complete in-flight requests even if banner becomes hidden
 * - Prefetch successful loads while hidden, show when visible
 * - No banner-level retries (network retries handled at lower layer)
 * - Proper resource cleanup on destroy
 *
 * Architecture: Divided into 4 specialized components for maintainability:
 * 1. VisibilityAwareRefreshClock - Handles visibility-driven timing (pause while hidden, no queuing)
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

    // 1. TIMING: Visibility-driven refresh that pauses while hidden (MVP requirement)
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
                // MVP: Show prefetched banners immediately when becoming visible
                if (visible && prefetched != null) {
                    presenter.show(prefetched!!)
                    prefetched = null
                    // Prevent immediate tick by marking as in-flight briefly
                    clock.markRequestStarted()
                    clock.setVisible(visible)
                    clock.markRequestFinished()
                } else {
                    // MVP: Inform clock of visibility changes for queuing logic
                    clock.setVisible(visible)
                }
            }
        }

    // MVP: Start visibility-driven timing (pauses while hidden)
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

            when (val result = loader.loadOnce()) {
                is Result.Success -> {
                    val banner = result.value
                    if (banner != null) {
                        // MVP: Show immediately if visible, prefetch if hidden
                        // Capture visibility at completion to avoid race conditions
                        if (gate.effective.value) {
                            presenter.show(banner)
                        } else {
                            // MVP: Complete request even if hidden - cache for later display
                            prefetched?.destroy()
                            prefetched = banner
                            CloudXLogger.d(TAG, placementName, placementId, "Prefetched while hidden")
                        }
                    }
                }

                is Result.Failure -> {
                    val error = result.value
                    when {
                        error.isNoFill -> {
                            // MVP: Emit error and wait for next interval (no banner retry)
                            listener?.onAdLoadFailed(CloudXAdError("No fill", CLXErrorCode.NO_FILL.code))
                        }
                        error.isTrafficControl -> {
                            // MVP: Ads disabled - emit error, continue refresh cycle
                            listener?.onAdLoadFailed(
                                CloudXAdError(
                                    "Ads disabled",
                                    CLXErrorCode.ADS_DISABLED.code
                                )
                            )
                        }
                        error.isTransientFailure -> {
                            // MVP: Network/5xx/4xx errors - emit error, continue refresh cycle
                            listener?.onAdLoadFailed(
                                CloudXAdError(
                                    "Temporary error",
                                    CLXErrorCode.SERVER_ERROR.code
                                )
                            )
                        }
                    }
                }
            }

            // MVP: Mark request finished to restart timer and avoid stacking
            clock.markRequestFinished()
        }
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

// Test-only factory to allow injecting a mock loader while keeping the implementation private.
@Suppress("TestFunctionName")
internal fun BannerManagerTestFactory(
    placementId: String,
    placementName: String,
    bannerVisibility: StateFlow<Boolean>,
    refreshSeconds: Int,
    connectionStatusService: ConnectionStatusService,
    appLifecycleService: AppLifecycleService,
    metricsTrackerNew: MetricsTrackerNew,
    loader: BannerAdLoader,
): BannerManager = BannerManagerImpl(
    placementId = placementId,
    placementName = placementName,
    bannerVisibility = bannerVisibility,
    refreshSeconds = refreshSeconds,
    connectionStatusService = connectionStatusService,
    appLifecycleService = appLifecycleService,
    metricsTrackerNew = metricsTrackerNew,
    loader = loader
)
