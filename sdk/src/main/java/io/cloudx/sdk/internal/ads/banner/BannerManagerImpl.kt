package io.cloudx.sdk.internal.ads.banner

import android.app.Activity
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.banner.parts.DefaultBannerPresenter
import io.cloudx.sdk.internal.ads.banner.parts.FreeRunningOneQueuedTickClock
import io.cloudx.sdk.internal.ads.banner.parts.VisibilityGate
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.decorate
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

internal class BannerManagerImpl(
    // === keep the original signature ===
    private val activity: Activity,
    private val placementId: String,
    private val placementName: String,
    private val bidAdSource: BidAdSource<BannerAdapterDelegate>, // unused (intentionally kept)
    private val bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    private val suspendPreloadWhenInvisible: Boolean, // unused (intentionally kept)
    private val preloadTimeMillis: Long,             // unused (intentionally kept)
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService,
    private val activityLifecycleService: ActivityLifecycleService, // unused (intentionally kept)
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

    // state
    private var inflight = false
    private var prefetched: BannerAdapterDelegate? = null
    private var pendingTick = false

    // components
    private val clock = FreeRunningOneQueuedTickClock(
        intervalMs = (refreshSeconds.coerceAtLeast(1) * 1000L),
        scope = scope
    )

    private val appForeground: StateFlow<Boolean> = appLifecycleService.isResumed
    private val gate = VisibilityGate(bannerVisibility, appForeground, scope)

    private val presenter = DefaultBannerPresenter(
        placementId = placementId,
        placementName = placementName,
        listener = listener,
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
        // react to effective visibility
        visJob?.cancel()
        visJob = scope.launch {
            gate.effective.collect { visible ->
                if (visible) {
                    prefetched?.let {
                        presenter.show(it)
                        prefetched = null
                    }
                    if (pendingTick && !inflight) {
                        pendingTick = false
                        launchRequest()
                    }
                }
            }
        }

        clock.start()

        tickJob?.cancel()
        tickJob = scope.launch {
            clock.ticks.collect {
                if (inflight) return@collect
                if (gate.effective.value) {
                    launchRequest()
                } else {
                    pendingTick = true // NEW: queue exactly one when hidden
                }
            }
        }

        // Kickoff immediately if visible; else queue one for when it becomes visible
        if (!inflight && gate.effective.value) {
            launchRequest()
        } else if (!gate.effective.value) {
            pendingTick = true
        }
    }

    private fun launchRequest() {
        inflight = true
        clock.markRequestStarted()

        reqJob?.cancel()
        reqJob = scope.launch {
            metricsTrackerNew.trackMethodCall(MetricsType.Method.BannerRefresh)
            try {
                connectionStatusService.awaitConnection()
            } catch (_: CancellationException) {
                inflight = false
                clock.markRequestFinished()
                return@launch
            }
            when (val outcome = loader.loadOnce()) {
                is BannerLoadOutcome.Success -> {
                    val banner = outcome.banner
                    if (gate.effective.value) {
                        presenter.show(banner)
                    } else {
                        prefetched?.destroy()
                        prefetched = banner
                        CloudXLogger.d(TAG, placementName, placementId, "Prefetched while hidden")
                    }
                }

                BannerLoadOutcome.NoFill -> {
                    listener?.onAdLoadFailed(CloudXAdError("No fill", CLXErrorCode.NO_FILL.code))
                    CloudXLogger.i(TAG, placementName, placementId, "NO_FILL this interval")
                }

                BannerLoadOutcome.TransientFailure -> {
                    // Retry is handled by network layer; banner waits for next tick
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Temporary error",
                            CLXErrorCode.SERVER_ERROR.code
                        )
                    )
                    CloudXLogger.w(TAG, placementName, placementId, "Transient failure")
                }

                BannerLoadOutcome.PermanentFailure -> {
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Permanent error",
                            CLXErrorCode.CLIENT_ERROR.code
                        )
                    )
                    CloudXLogger.w(TAG, placementName, placementId, "Permanent failure")
                    // optional: mark placement disabled here
                }

                BannerLoadOutcome.TrafficControl -> {
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Ads disabled",
                            CLXErrorCode.ADS_DISABLED.code
                        )
                    )
                    CloudXLogger.w(
                        TAG,
                        placementName,
                        placementId,
                        "Traffic control (ADS disabled)"
                    )
                }
            }

            inflight = false
            clock.markRequestFinished()
        }
    }

    override fun destroy() {
        scope.cancel()
        tickJob?.cancel()
        visJob?.cancel()
        reqJob?.cancel()
        clock.stop()

        prefetched?.destroy()
        presenter.destroy()
        prefetched = null

        CloudXLogger.d(TAG, placementName, placementId, "Destroyed")
    }
}
