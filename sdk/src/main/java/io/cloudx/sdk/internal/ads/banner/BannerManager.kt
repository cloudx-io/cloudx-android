package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.ads.AdLoader
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsType
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal interface BannerManager : Destroyable {
    var listener: CloudXAdViewListener?
}

private class BannerManagerImpl(
    private val placementId: String,
    private val placementName: String,
    private val adLoader: AdLoader<BannerAdapterDelegate>,
    bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    suspendPreloadWhenInvisible: Boolean,
    private val metricsTracker: MetricsTracker,
) : BannerManager {

    // Core properties
    private val TAG = "BannerManager"
    private val scope = CoroutineScope(Dispatchers.Main)
    override var listener: CloudXAdViewListener? = null

    // Timing configuration
    private val refreshDelayMillis = TimeUnit.SECONDS.toMillis(refreshSeconds.toLong())

    // Banner refresh management
    private val bannerRefreshTimer =
        BannerSuspendableTimer(
            bannerVisibility,
            suspendPreloadWhenInvisible
        )
    private var bannerRefreshJob: Job? = null

    // Backup banner management
    private val backupBanner = MutableStateFlow<Result<BannerAdapterDelegate, CloudXError>?>(null)
    private var backupBannerLoadJob: Job? = null
    private var backupBannerErrorHandlerJob: Job? = null

    // Current banner management
    private var currentBanner: BannerAdapterDelegate? = null
    private var currentBannerEventHandlerJob: Job? = null

    init {
        restartBannerRefresh()
    }

    // Banner refresh methods
    private fun restartBannerRefresh() {
        bannerRefreshJob?.cancel()
        bannerRefreshJob = scope.launch {
            while (true) {
                ensureActive()

                val banner = loadBackupBanner()
                banner.fold(
                    onSuccess = {
                        hideAndDestroyCurrentBanner()
                        showNewBanner(it)
                        listener?.onAdLoaded(it)
                    },
                    onFailure = {
                        listener?.onAdLoadFailed(it)
                    }
                )

                metricsTracker.trackMethodCall(MetricsType.Method.BannerRefresh)

                CXLogger.d(
                    TAG,
                    placementName,
                    placementId,
                    "Banner refresh scheduled in ${refreshSeconds}s"
                )
                bannerRefreshTimer.awaitTimeout(refreshDelayMillis)
            }
        }
    }

    // Backup banner methods
    private fun loadBackupBannerIfAbsent() {
        if (backupBanner.value is Result.Success || backupBannerLoadJob?.isActive == true) {
            return
        }

        CXLogger.d(TAG, placementName, placementId, "Loading backup banner")
        backupBannerLoadJob = scope.launch {
            /**
             * Each returned banner from this method should be already attached to the BannerContainer in CloudXAdView.
             * If you look at the implementation of CloudXAdView::createBannerContainer()
             * you'll see that each banner gets inserted to the "background" of the view hence can be treated as invisible/precached.
             * So, first successful non-null tryLoadBanner() call will result in banner displayed on the screen.
             * All the consecutive successful tryLoadBanner() calls will result in banner attached to the background and visibility set to GONE.
             * Once the foreground visible banner is destroyed (banner.destroy())
             * it gets removed from the screen and the next topmost banner gets displayed if available.
             */
            val banner = adLoader.load()
            backupBanner.value = banner
            banner.onSuccess {
                preserveBackupBanner(it)
            }
        }
    }

    private fun preserveBackupBanner(banner: BannerAdapterDelegate) {
        CXLogger.d(TAG, placementName, placementId, "Backup banner loaded and ready")

        backupBannerErrorHandlerJob?.cancel()
        backupBannerErrorHandlerJob = scope.launch {
            val error = banner.lastErrorEvent.first { it != null }
            CXLogger.w(
                TAG,
                placementName,
                placementId,
                "Backup banner error detected: $error - destroying and reloading"
            )
            destroyBackupBanner()
            loadBackupBannerIfAbsent()
        }
    }

    private fun destroyBackupBanner() {
        backupBannerErrorHandlerJob?.cancel()

        with(backupBanner) {
            value?.onSuccess {
                CXLogger.d(TAG, placementName, placementId, "Destroying backup banner")
                it.destroy()
            }
            value = null
        }
    }

    private suspend fun loadBackupBanner(): Result<BannerAdapterDelegate, CloudXError> {
        loadBackupBannerIfAbsent()

        val banner = backupBanner.mapNotNull { it }.first()

        backupBannerErrorHandlerJob?.cancel()
        backupBanner.value = null

        return banner
    }

    // Current banner management methods
    private fun showNewBanner(banner: BannerAdapterDelegate) {
        CXLogger.d(TAG, placementName, placementId, "Displaying new banner")
        listener?.onAdDisplayed(banner)

        currentBanner = banner

        currentBannerEventHandlerJob?.cancel()
        currentBannerEventHandlerJob = scope.launch {
            launch {
                banner.event.filter { it == BannerAdapterDelegateEvent.Click }.collect {
                    CXLogger.i(TAG, placementName, placementId, "Banner clicked by user")
                    listener?.onAdClicked(banner)
                }
            }
            launch {
                val error = banner.lastErrorEvent.first { it != null }
                CXLogger.w(
                    TAG,
                    placementName,
                    placementId,
                    "Banner error detected: $error - restarting refresh cycle"
                )
                error?.let { listener?.onAdDisplayFailed(it) }
                hideAndDestroyCurrentBanner()
                restartBannerRefresh()
            }
        }
    }

    private fun hideAndDestroyCurrentBanner() {
        currentBanner?.let {
            CXLogger.d(TAG, placementName, placementId, "Hiding current banner")
            listener?.onAdHidden(it)
        }
        destroyCurrentBanner()
    }

    private fun destroyCurrentBanner() {
        currentBannerEventHandlerJob?.cancel()

        currentBanner?.let {
            CXLogger.d(TAG, placementName, placementId, "Destroying current banner")
            it.destroy()
        }
        currentBanner = null
    }

    // Cleanup methods
    override fun destroy() {
        scope.cancel()

        destroyCurrentBanner()
        bannerRefreshTimer.destroy()

        destroyBackupBanner()
    }
}

internal fun BannerManager(
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
    metricsTracker: MetricsTracker,
    winLossTracker: WinLossTracker,
    connectionStatusService: ConnectionStatusService,
    accountId: String,
    appKey: String
): BannerManager {

    val bidRequestProvider = BidRequestProvider(
        bidRequestExtrasProviders
    )

    val bidSource =
        BidBannerSource(
            adViewContainer = adViewContainer,
            refreshSeconds = refreshSeconds,
            factories = bidFactories,
            placementId = placementId,
            placementName = placementName,
            placementType = adType,
            requestBid = bidApi,
            cdpApi = cdpApi,
            generateBidRequest = bidRequestProvider,
            eventTracker = eventTracker,
            metricsTracker = metricsTracker,
            winLossTracker = winLossTracker,
            miscParams = miscParams,
            bidRequestTimeoutMillis = 0,
            accountId = accountId,
            appKey = appKey
        )


    val adLoader = AdLoader(
        bidAdSource = bidSource,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        placementName = placementName,
        placementId = placementId,
        connectionStatusService = connectionStatusService,
        winLossTracker = winLossTracker
    )

    return BannerManagerImpl(
        placementId = placementId,
        placementName = placementName,
        adLoader = adLoader,
        bannerVisibility = bannerVisibility,
        refreshSeconds = refreshSeconds,
        suspendPreloadWhenInvisible = true,
        metricsTracker = metricsTracker
    )
}
