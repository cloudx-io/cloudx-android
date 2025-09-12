package io.cloudx.sdk.internal.ads.banner

import android.app.Activity
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.ads.banner.components.BannerAdLoader
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
        activity = activity,
        placementId = placementId,
        placementName = placementName,
        loader = loader,
        bannerVisibility = bannerVisibility,
        refreshSeconds = refreshSeconds,
        suspendPreloadWhenInvisible = true,
        connectionStatusService = connectionStatusService,
        activityLifecycleService = ActivityLifecycleService(),
        appLifecycleService = appLifecycleService,
        metricsTrackerNew = metricsTrackerNew
    )
}

private class BannerManagerImpl(
    private val activity: Activity,
    private val placementId: String,
    private val placementName: String,
    private val loader: BannerAdLoader,
    private val bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    private val suspendPreloadWhenInvisible: Boolean,
    private val connectionStatusService: ConnectionStatusService,
    private val activityLifecycleService: ActivityLifecycleService,
    private val appLifecycleService: AppLifecycleService,
    private val metricsTrackerNew: MetricsTrackerNew,
) : BannerManager {

    private val TAG = "BannerManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override var listener: CloudXAdViewListener? = null
        set(listener) {
            field = listener?.decorate()
        }

    private var refreshJob: Job? = null
    private var loadJob: Job? = null

    // Track effective visibility (banner + app)
    private var currentlyVisible = false

    private val refreshDelayMillis = refreshSeconds * 1000L

    // Timer for refresh intervals
    private val refreshTimer = BannerSuspendableTimer(
        activity = activity,
        bannerContainerVisibility = bannerVisibility,
        activityLifecycleService = activityLifecycleService,
        suspendWhenInvisible = suspendPreloadWhenInvisible
    )

    init {
        CloudXLogger.i(
            TAG,
            placementName,
            placementId,
            "BannerManager initialized - refresh: ${refreshSeconds}s"
        )

        // Track effective visibility (banner + activity lifecycle)
        scope.launch {
            combine(
                bannerVisibility,
                activityLifecycleService.currentResumedActivity
            ) { banner, currentActivity ->
                banner && currentActivity == activity
            }.collect { visible ->
                currentlyVisible = visible
                if (visible && cachedBanner != null) {
                    // Show cached banner when becoming visible
                    cachedBanner?.let { cached ->
                        hideAndDestroyCurrentBanner()
                        showNewBanner(cached)
                        cachedBanner = null
                    }
                }
            }
        }

        startRefreshCycle()
    }

    private fun startRefreshCycle() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                ensureActive()

                // MVP: Load banner at refresh interval
                val banner = loadBannerNow()

                if (banner != null) {
                    hideAndDestroyCurrentBanner()
                    showNewBanner(banner)
                }

                metricsTrackerNew.trackMethodCall(MetricsType.Method.BannerRefresh)

                CloudXLogger.d(
                    TAG,
                    placementName,
                    placementId,
                    "Banner refresh scheduled in ${refreshSeconds}s"
                )
                // MVP: Wait full interval before next request
                refreshTimer.awaitTimeout(refreshDelayMillis)
            }
        }
    }

    // MVP: Simple cache - only used when request completes while hidden
    private var cachedBanner: BannerAdapterDelegate? = null
    private var visibilityJob: Job? = null

    private suspend fun loadBannerNow(): BannerAdapterDelegate? {
        // MVP: Single-flight - cancel any existing load
        loadJob?.cancel()

        // Check if we have a cached banner from previous hidden load
        cachedBanner?.let { cached ->
            CloudXLogger.d(TAG, placementName, placementId, "Using cached banner")
            cachedBanner = null
            return cached
        }

        return try {
            connectionStatusService.awaitConnection()

            when (val result = loader.loadOnce()) {
                is Result.Success -> {
                    val banner = result.value
                    if (banner != null) {
                        // MVP: Check visibility at completion
                        if (isCurrentlyVisible()) {
                            // Still visible - return to show immediately
                            banner
                        } else {
                            // MVP: "If banner becomes hidden during request, cache for next visible"
                            CloudXLogger.d(
                                TAG,
                                placementName,
                                placementId,
                                "Banner loaded while hidden - caching"
                            )
                            cachedBanner = banner
                            // Cache set, visibility watcher in init will show it when visible
                            null
                        }
                    } else {
                        null
                    }
                }

                is Result.Failure -> {
                    // MVP: "On failure, emit error and wait for next interval"
                    handleLoadError(result.value)
                    null
                }
            }
        } catch (e: CancellationException) {
            null
        }
    }

    private fun isCurrentlyVisible(): Boolean {
        return currentlyVisible
    }


    private fun handleLoadError(error: CLXError) {
        when {
            // MVP: No fill - emit error and maintain refresh cadence
            error.isNoFill -> {
                listener?.onAdLoadFailed(CloudXAdError("No fill", CLXErrorCode.NO_FILL.code))
            }
            // MVP: Ads disabled - emit error, continue refresh cycle
            error.isTrafficControl -> {
                listener?.onAdLoadFailed(
                    CloudXAdError(
                        "Ads disabled",
                        CLXErrorCode.ADS_DISABLED.code
                    )
                )
            }
            // MVP: Network/5xx/4xx errors - emit error, continue refresh cycle
            error.isTransientFailure -> {
                listener?.onAdLoadFailed(
                    CloudXAdError(
                        "Temporary error",
                        CLXErrorCode.SERVER_ERROR.code
                    )
                )
            }
        }
    }

    private var currentBanner: BannerAdapterDelegate? = null
    private var currentBannerEventHandlerJob: Job? = null

    private fun showNewBanner(banner: BannerAdapterDelegate) {
        CloudXLogger.d(TAG, placementName, placementId, "Displaying new banner")
        listener?.onAdDisplayed(banner)

        currentBanner = banner

        currentBannerEventHandlerJob?.cancel()
        currentBannerEventHandlerJob = scope.launch {
            launch {
                banner.event.filter { it == BannerAdapterDelegateEvent.Click }.collect {
                    CloudXLogger.i(TAG, placementName, placementId, "Banner clicked by user")
                    listener?.onAdClicked(banner)
                }
            }
            launch {
                val error = banner.lastErrorEvent.first { it != null }
                CloudXLogger.w(
                    TAG,
                    placementName,
                    placementId,
                    "Banner error detected: $error - restarting refresh cycle"
                )
                hideAndDestroyCurrentBanner()
                startRefreshCycle()
            }
        }
    }

    private fun hideAndDestroyCurrentBanner() {
        currentBanner?.let {
            CloudXLogger.d(TAG, placementName, placementId, "Hiding current banner")
            listener?.onAdHidden(it)
        }
        destroyCurrentBanner()
    }

    private fun destroyCurrentBanner() {
        currentBannerEventHandlerJob?.cancel()

        currentBanner?.let {
            CloudXLogger.d(TAG, placementName, placementId, "Destroying current banner")
            it.destroy()
        }
        currentBanner = null
    }

    // MVP: Resource cleanup - cancels timers, requests, clears cached creatives
    override fun destroy() {
        scope.cancel()

        refreshJob?.cancel()
        loadJob?.cancel()
        visibilityJob?.cancel()

        destroyCurrentBanner()
        refreshTimer.destroy()

        // Clear cached banner
        cachedBanner?.destroy()
        cachedBanner = null
    }
}