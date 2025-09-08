package io.cloudx.sdk.internal.ads.banner

import android.app.Activity
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.decorate
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal class BannerManagerImpl(
    private val activity: Activity,
    private val placementId: String,
    private val placementName: String,
    private val bidAdSource: BidAdSource<BannerAdapterDelegate>,
    private val bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    private val suspendPreloadWhenInvisible: Boolean,
    preloadTimeMillis: Long,
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService,
    private val activityLifecycleService: ActivityLifecycleService,
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

    // State
    private var isVisible = false
    private var inflight = false
    private var pendingTick = false
    private var prefetched: BannerAdapterDelegate? = null
    private var current: BannerAdapterDelegate? = null

    private var timerJob: Job? = null
    private var visJob: Job? = null
    private var requestJob: Job? = null
    private var eventsJob: Job? = null

    init {
        startTimer()
        observeVisibility()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            val periodMs = (refreshSeconds * 1000L).coerceAtLeast(0)
            while (isActive) {
                delay(periodMs)
                onTick()
            }
        }
    }

    private fun observeVisibility() {
        visJob?.cancel()
        visJob = scope.launch {
            bannerVisibility.collectLatest { visible ->
                isVisible = visible
                if (visible) {
                    // Show prefetched if any
                    prefetched?.let { show(it); prefetched = null }
                    // Consume exactly one queued tick if idle
                    if (pendingTick && !inflight) {
                        pendingTick = false
                        startRequest()
                    }
                }
            }
        }
    }

    private fun onTick() {
        when {
            inflight -> pendingTick = true                         // queue exactly one
            isVisible -> startRequest()
            else -> pendingTick = true                              // hidden → queue one
        }
    }

    private fun startRequest() {
        inflight = true
        requestJob?.cancel()
        requestJob = scope.launch {
            try {
                connectionStatusService.awaitConnection()
            } catch (_: CancellationException) {
                inflight = false; return@launch
            }

            val banner = loader.loadOnce()
            metricsTrackerNew.trackMethodCall(MetricsType.Method.BannerRefresh)

            inflight = false

            if (banner == null) {
                // emit no fill and keep cadence
//                listener?.onAdLoadFailed(CloudXAdError())
            } else {
                if (isVisible) show(banner) else {
                    prefetched?.destroy()
                    prefetched = banner
                }
            }

            // If a single tick was queued during request, consume it now
            if (pendingTick && isVisible && !inflight) {
                pendingTick = false
                startRequest()
            }
        }
    }

    private fun show(banner: BannerAdapterDelegate) {
        // tear down current
        eventsJob?.cancel()
        current?.let {
            listener?.onAdHidden(it)
            it.destroy()
        }
        current = banner

        CloudXLogger.d(TAG, placementName, placementId, "Displaying banner")
        listener?.onAdDisplayed(banner)

        // wire minimal events; cadence remains driven by timer
        eventsJob = scope.launch {
            launch {
                banner.event.collect { ev ->
                    if (ev == BannerAdapterDelegateEvent.Click) listener?.onAdClicked(banner)
                }
            }
            launch {
                eventsJob?.cancel()
                current?.destroy()
                current = null
                listener?.onAdHidden(banner)
                // do not start new request here; timer/pendingTick controls cadence
            }
        }
    }

    override fun destroy() {
        scope.cancel()
        eventsJob?.cancel()
        requestJob?.cancel()
        timerJob?.cancel()
        visJob?.cancel()

        prefetched?.destroy()
        current?.destroy()
        prefetched = null
        current = null
    }
}