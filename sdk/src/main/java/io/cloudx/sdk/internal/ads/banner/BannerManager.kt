package io.cloudx.sdk.internal.ads.banner

import android.app.Activity
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.BidAdSourceResponse
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.bid.LoadResult
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.bid.LossTracker
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

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
    metricsTracker: MetricsTracker,
    connectionStatusService: ConnectionStatusService,
    activityLifecycleService: ActivityLifecycleService,
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
            metricsTracker,
            miscParams,
            0,
            accountId,
            appKey
        )

    return BannerManagerImpl(
        activity = activity,
        placementId = placementId,
        placementName = placementName,
        bidAdSource = bidSource,
        bannerVisibility = bannerVisibility,
        refreshSeconds = refreshSeconds,
        suspendPreloadWhenInvisible = true,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        connectionStatusService = connectionStatusService,
        activityLifecycleService = activityLifecycleService,
        appLifecycleService = appLifecycleService,
        metricsTracker = metricsTracker
    )
}

private class BannerManagerImpl(
    private val activity: Activity,
    private val placementId: String,
    private val placementName: String,
    private val bidAdSource: BidAdSource<BannerAdapterDelegate>,
    bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    private val suspendPreloadWhenInvisible: Boolean,
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService,
    private val activityLifecycleService: ActivityLifecycleService,
    private val appLifecycleService: AppLifecycleService,
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
            activity,
            bannerVisibility,
            activityLifecycleService,
            suspendPreloadWhenInvisible
        )
    private var bannerRefreshJob: Job? = null

    // Backup banner management
    private val backupBanner = MutableStateFlow<Result<BannerAdapterDelegate, Unit>?>(null)
    private var backupBannerLoadJob: Job? = null
    private var backupBannerErrorHandlerJob: Job? = null

    // Current banner management
    private var currentBanner: BannerAdapterDelegate? = null
    private var currentBannerEventHandlerJob: Job? = null

    init {
        CloudXLogger.i(
            TAG,
            placementName,
            placementId,
            "BannerManager initialized - refresh: ${refreshSeconds}s, timeout: ${bidAdLoadTimeoutMillis}ms"
        )
        restartBannerRefresh()
    }

    // Banner refresh methods
    private fun restartBannerRefresh() {
        bannerRefreshJob?.cancel()
        bannerRefreshJob = scope.launch {
            while (true) {
                ensureActive()

                val banner = awaitBackupBanner()
                banner.successOrNull()?.let {
                    hideAndDestroyCurrentBanner()
                    showNewBanner(it)
                }

                metricsTracker.trackMethodCall(MetricsType.Method.BannerRefresh)

                CloudXLogger.d(
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

        CloudXLogger.d(TAG, placementName, placementId, "Loading backup banner")
        backupBannerLoadJob = scope.launch {
            val banner = loadNewBanner()
            backupBanner.value = banner
            banner.successOrNull()?.let {
                preserveBackupBanner(it)
            }
        }
    }

    private fun preserveBackupBanner(banner: BannerAdapterDelegate) {
        CloudXLogger.d(TAG, placementName, placementId, "Backup banner loaded and ready")

        backupBannerErrorHandlerJob?.cancel()
        backupBannerErrorHandlerJob = scope.launch {
            val error = banner.lastErrorEvent.first { it != null }
            CloudXLogger.w(
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
            value?.successOrNull()?.let {
                CloudXLogger.d(TAG, placementName, placementId, "Destroying backup banner")
                it.destroy()
            }
            value = null
        }
    }

    private suspend fun awaitBackupBanner(): Result<BannerAdapterDelegate, Unit> {
        loadBackupBannerIfAbsent()

        val banner = backupBanner.mapNotNull { it }.first()

        backupBannerErrorHandlerJob?.cancel()
        backupBanner.value = null

        return banner
    }

    // Banner loading methods
    // Each returned banner from this method should be already attached to the BannerContainer in CloudXAdView.
    // If you look at the implementation of CloudXAdView::createBannerContainer()
    // you'll see that each banner gets inserted to the "background" of the view hence can be treated as invisible/precached.
    // So, first successful non-null tryLoadBanner() call will result in banner displayed on the screen.
    // All the consecutive successful tryLoadBanner() calls will result in banner attached to the background and visibility set to GONE.
    // Once the foreground visible banner is destroyed (banner.destroy())
    // it gets removed from the screen and the next topmost banner gets displayed if available.
    private suspend fun loadNewBanner(): Result<BannerAdapterDelegate, Unit> = coroutineScope {
        ensureActive()

        val bids: BidAdSourceResponse<BannerAdapterDelegate>? = bidAdSource.requestBid()

        if (bids != null) {
            CloudXLogger.i(
                TAG,
                placementName,
                placementId,
                "Received ${bids.bidItemsByRank.size} bids from auction"
            )
        } else {
            CloudXLogger.w(
                TAG,
                placementName,
                placementId,
                "No bids available from auction"
            )
        }

        bids?.loadOrDestroyBanner()?.let {
            Result.Success(it)
        } ?: Result.Failure(Unit)
    }

    /**
     * Trying to load the top rank (1) bid; load the next top one otherwise.
     */
    private suspend fun BidAdSourceResponse<BannerAdapterDelegate>.loadOrDestroyBanner(): BannerAdapterDelegate? =
        coroutineScope {
            var loadedBanner: BannerAdapterDelegate? = null
            var winnerIndex: Int = -1

            val lossReasons = mutableMapOf<String, LossReason>()

            for ((index, bidItem) in bidItemsByRank.withIndex()) {
                ensureActive()

                CloudXLogger.d(
                    TAG,
                    placementName,
                    placementId,
                    "Attempting to load bid: ${bidItem.adNetworkOriginal.networkName}, CPM: $${bidItem.price}, rank: ${bidItem.rank}"
                )

                val result = loadOrDestroyBanner(bidAdLoadTimeoutMillis, bidItem.createBidAd)
                val banner = result.banner

                if (banner != null) {
                    CloudXLogger.i(
                        TAG,
                        placementName,
                        placementId,
                        "Successfully loaded bid: ${bidItem.adNetworkOriginal.networkName}, CPM: $${bidItem.price}, rank: ${bidItem.rank}"
                    )
                    loadedBanner = banner
                    winnerIndex = index
                    break
                } else {
                    CloudXLogger.w(
                        TAG,
                        placementName,
                        placementId,
                        "Failed to load bid: ${bidItem.adNetworkOriginal.networkName}, CPM: $${bidItem.price}, rank: ${bidItem.rank}"
                    )
                    lossReasons[bidItem.id] = LossReason.TechnicalError
                }
            }

            if (winnerIndex != -1) {
                // Mark all other bids as "Lost to higher bid"
                bidItemsByRank.forEachIndexed { index, bidItem ->
                    if (index != winnerIndex && !lossReasons.containsKey(bidItem.id)) {
                        lossReasons[bidItem.id] = LossReason.LostToHigherBid
                    }
                }

                // Fire lurls
                bidItemsByRank.forEachIndexed { index, bidItem ->
                    if (index != winnerIndex) {
                        val reason = lossReasons[bidItem.id] ?: return@forEachIndexed

                        if (!bidItem.lurl.isNullOrBlank()) {
                            CloudXLogger.d(
                                TAG,
                                placementName,
                                placementId,
                                "Calling LURL for ${bidItem.adNetwork}, reason=${reason.name}, rank=${bidItem.rank}"
                            )

                            LossTracker.trackLoss(bidItem.lurl, reason)
                        }
                    }
                }
            }

            loadedBanner
        }

    // returns: null - banner wasn't loaded.
    private suspend fun loadOrDestroyBanner(
        loadTimeoutMillis: Long,
        createBanner: suspend () -> BannerAdapterDelegate
    ): LoadResult {
        // TODO. Replace runCatching with actual check whether ad can be created based on parameters
        //  For instance:
        //  Create AdColony ad when there's no AdColony ad in this module or init parameters are invalid.
        val banner = try {
            createBanner()
        } catch (e: Exception) {
            return LoadResult(null, LossReason.TechnicalError)
        }

        var isBannerLoaded = false

        try {
            if (suspendPreloadWhenInvisible) {
                activityLifecycleService.awaitActivityResume(activity)
            } else {
                appLifecycleService.awaitAppResume()
            }

            connectionStatusService.awaitConnection()

            CloudXLogger.d(
                TAG,
                placementName,
                placementId,
                "Starting banner load with ${loadTimeoutMillis}ms timeout"
            )
            isBannerLoaded = withTimeout(loadTimeoutMillis) { banner.load() }

        } catch (e: TimeoutCancellationException) {
            CloudXLogger.w(
                TAG,
                placementName,
                placementId,
                "Banner load timeout after ${loadTimeoutMillis}ms",
                e
            )
            banner.timeout()
            return LoadResult(null, LossReason.TechnicalError)
        } catch (e: Exception) {
            CloudXLogger.e(TAG, placementName, placementId, "Banner load failed with exception", e)
            return LoadResult(null, LossReason.TechnicalError)
        } finally {
            if (!isBannerLoaded) {
                CloudXLogger.d(TAG, placementName, placementId, "Destroying failed banner")
                banner.destroy()
            }
        }

        return LoadResult(banner, null) // No loss reason, we have a winner
    }

    // Current banner management methods
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
                restartBannerRefresh()
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

    // Cleanup methods
    override fun destroy() {
        scope.cancel()

        destroyCurrentBanner()
        bannerRefreshTimer.destroy()

        destroyBackupBanner()
    }
}

