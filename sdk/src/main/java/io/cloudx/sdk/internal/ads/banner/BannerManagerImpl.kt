package io.cloudx.sdk.internal.ads.banner

import VisibilityAwareOneQueuedClock
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.banner.parts.BannerAdLoader
import io.cloudx.sdk.internal.ads.banner.parts.BannerLoadOutcome
import io.cloudx.sdk.internal.ads.banner.parts.DefaultBannerPresenter
import io.cloudx.sdk.internal.ads.banner.parts.VisibilityGate
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

    private var prefetched: BannerAdapterDelegate? = null

    // components
    private val clock = VisibilityAwareOneQueuedClock(
        intervalMs = (refreshSeconds.coerceAtLeast(1) * 1000L),
        scope = scope
    )

    private val appForeground: StateFlow<Boolean> = appLifecycleService.isResumed
    private val gate = VisibilityGate(bannerVisibility, appForeground, scope)

    private val presenter = DefaultBannerPresenter(
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
        // react to effective visibility
        visJob?.cancel()
        visJob = scope.launch {
            gate.effective.collect { visible ->
                // 1) inform the clock
                clock.setVisible(visible)
                // 2) show any prefetched immediately
                if (visible) {
                    prefetched?.let { presenter.show(it); prefetched = null }
                }
            }
        }

        clock.start()

        tickJob?.cancel()
        tickJob = scope.launch {
            clock.ticks.collect {
                launchRequest()
            }
        }
    }

    private fun launchRequest() {
        clock.markRequestStarted()
        reqJob?.cancel()
        reqJob = scope.launch {
            metricsTrackerNew.trackMethodCall(MetricsType.Method.BannerRefresh)
            try {
                connectionStatusService.awaitConnection()
            } catch (_: CancellationException) {
                clock.markRequestFinished()
                return@launch
            }

            when (val outcome = loader.loadOnce()) {
                is BannerLoadOutcome.Success -> {
                    if (gate.effective.value) presenter.show(outcome.banner)
                    else {
                        prefetched?.destroy(); prefetched = outcome.banner
                        CloudXLogger.d(TAG, placementName, placementId, "Prefetched while hidden")
                    }
                }

                BannerLoadOutcome.NoFill ->
                    listener?.onAdLoadFailed(CloudXAdError("No fill", CLXErrorCode.NO_FILL.code))

                BannerLoadOutcome.TransientFailure ->
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Temporary error",
                            CLXErrorCode.SERVER_ERROR.code
                        )
                    )

                BannerLoadOutcome.PermanentFailure ->
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Permanent error",
                            CLXErrorCode.CLIENT_ERROR.code
                        )
                    )

                BannerLoadOutcome.TrafficControl ->
                    listener?.onAdLoadFailed(
                        CloudXAdError(
                            "Ads disabled",
                            CLXErrorCode.ADS_DISABLED.code
                        )
                    )
            }

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
