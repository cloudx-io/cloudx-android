package io.cloudx.sdk.internal

import android.app.Activity
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.BannerFactoryMiscParams
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterFactory
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.bid.BidApi
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.bid.LoadResult
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.bid.LossReporter
import io.cloudx.sdk.internal.cdp.CdpApi
import io.cloudx.sdk.internal.common.BidBackoffMechanism
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSource
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSourceResponse
import io.cloudx.sdk.internal.core.ad.source.bid.BidBannerSource
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableBanner
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableBannerEvent
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal interface Banner : Destroyable {

    var listener: CloudXAdViewListener?
}

internal fun Banner(
    activity: Activity,
    placementId: String,
    placementName: String,
    adViewContainer: CloudXAdViewAdapterContainer,
    bannerVisibility: StateFlow<Boolean>,
    refreshSeconds: Int,
    adType: AdType,
    preloadTimeMillis: Long,
    bidFactories: Map<AdNetwork, CloudXAdViewAdapterFactory>,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>,
    bidMaxBackOffTimeMillis: Long,
    bidAdLoadTimeoutMillis: Long,
    miscParams: BannerFactoryMiscParams,
    bidApi: BidApi,
    cdpApi: CdpApi,
    eventTracker: EventTracker,
    metricsTrackerNew: MetricsTrackerNew,
    connectionStatusService: ConnectionStatusService,
    activityLifecycleService: ActivityLifecycleService,
    appLifecycleService: AppLifecycleService,
    accountId: String,
    appKey: String
): Banner {

    val bidRequestProvider = BidRequestProvider(
        activity,
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

    return BannerImpl(
        activity = activity,
        bidAdSource = bidSource,
        bannerVisibility = bannerVisibility,
        refreshSeconds = refreshSeconds,
        suspendPreloadWhenInvisible = true,
        preloadTimeMillis = preloadTimeMillis,
        bidMaxBackOffTimeMillis = bidMaxBackOffTimeMillis,
        bidAdLoadTimeoutMillis = bidAdLoadTimeoutMillis,
        connectionStatusService = connectionStatusService,
        activityLifecycleService = activityLifecycleService,
        appLifecycleService = appLifecycleService,
        metricsTrackerNew = metricsTrackerNew
    )
}

private class BannerImpl(
    private val activity: Activity,
    private val bidAdSource: BidAdSource<SuspendableBanner>,
    bannerVisibility: StateFlow<Boolean>,
    private val refreshSeconds: Int,
    private val suspendPreloadWhenInvisible: Boolean,
    preloadTimeMillis: Long,
    bidMaxBackOffTimeMillis: Long,
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService,
    private val activityLifecycleService: ActivityLifecycleService,
    private val appLifecycleService: AppLifecycleService,
    private val metricsTrackerNew: MetricsTrackerNew,
) : Banner {

    private val TAG = "BannerImpl"

    private val scope = CoroutineScope(Dispatchers.Main)

    private val bidBackoffMechanism = BidBackoffMechanism()

    override var listener: CloudXAdViewListener? = null
        set(listener) {
            field = listener?.decorate()
        }

    init {
        restartBannerRefresh()
    }

    private val bannerRefreshTimer =
        BannerSuspendableTimer(
            activity,
            bannerVisibility,
            activityLifecycleService,
            suspendPreloadWhenInvisible
        )

    private var bannerRefreshJob: Job? = null

    private val refreshDelayMillis = refreshSeconds * 1000L
    private val preloadDelayMillis = (refreshDelayMillis - preloadTimeMillis).coerceAtLeast(0)

    private fun restartBannerRefresh() {
        bannerRefreshJob?.cancel()
        bannerRefreshJob = scope.launch {
            while (true) {
                ensureActive()

                val banner = awaitBackupBanner()

                hideAndDestroyCurrentBanner()
                showNewBanner(banner)

                loadBackupBannerIfAbsent(delayLoadMillis = preloadDelayMillis)

                metricsTrackerNew.trackMethodCall(MetricsType.Method.BannerRefresh)

                CloudXLogger.debug(TAG, "Banner refresh scheduled in ${refreshSeconds}s")
                bannerRefreshTimer.awaitTimeout(refreshDelayMillis)
            }
        }
    }

    private val backupBanner = MutableStateFlow<SuspendableBanner?>(null)
    private var backupBannerLoadJob: Job? = null
    private val backupBannerLoadTimer =
        BannerSuspendableTimer(
            activity,
            bannerVisibility,
            activityLifecycleService,
            suspendPreloadWhenInvisible
        )

    private fun loadBackupBannerIfAbsent(delayLoadMillis: Long = 0) {
        if (backupBanner.value != null || backupBannerLoadJob?.isActive == true) {
            return
        }

        backupBannerLoadJob = scope.launch {
            backupBannerLoadTimer.awaitTimeout(delayLoadMillis)

            val banner = loadNewBanner()

            preserveBackupBanner(banner)
        }
    }

    private var backupBannerErrorHandlerJob: Job? = null

    private fun preserveBackupBanner(banner: SuspendableBanner) {
        backupBanner.value = banner

        backupBannerErrorHandlerJob?.cancel()
        backupBannerErrorHandlerJob = scope.launch {
            banner.lastErrorEvent.first { it != null }
            destroyBackupBanner()
            loadBackupBannerIfAbsent()
        }
    }

    private fun destroyBackupBanner() {
        backupBannerErrorHandlerJob?.cancel()

        with(backupBanner) {
            value?.destroy()
            value = null
        }
    }

    private suspend fun awaitBackupBanner(): SuspendableBanner {
        loadBackupBannerIfAbsent()

        val banner = backupBanner.mapNotNull { it }.first()

        backupBannerErrorHandlerJob?.cancel()
        backupBanner.value = null

        return banner
    }

    // Note. Since I'm a douche and don't want to refactor and write a coherent and concise code
    // here's the explanation on what's going on here:
    // Each returned banner from this method should be already attached to the BannerContainer in CloudXAdView.
    // If you look at the implementation of CloudXAdView::createBannerContainer()
    // you'll see that each banner gets inserted to the "background" of the view hence can be treated as invisible/precached.
    // So, first successful non-null tryLoadBanner() call will result in banner displayed on the screen.
    // All the consecutive successful tryLoadBanner() calls will result in banner attached to the background and visibility set to GONE.
    // Once the foreground visible banner is destroyed (banner.destroy())
    // it gets removed from the screen and the next topmost banner gets displayed if available.
    private suspend fun loadNewBanner(): SuspendableBanner = coroutineScope {
        var loadedBanner: SuspendableBanner? = null

        while (loadedBanner == null) {
            ensureActive()

            val bids: BidAdSourceResponse<SuspendableBanner>? = bidAdSource.requestBid()

            loadedBanner = bids?.loadOrDestroyBanner()

            if (loadedBanner == null) {
                bidBackoffMechanism.notifySoftError()

                // Delay after each batch of 3 fails
                CloudXLogger.debug(TAG, "Soft error delay for ${bidBackoffMechanism.getBatchDelay()}ms (batch)")
                delay(bidBackoffMechanism.getBatchDelay())

                if (bidBackoffMechanism.isBatchEnd) {
                    CloudXLogger.debug(TAG, "Batch of 3 soft errors: Delaying for ${bidBackoffMechanism.getBarrierDelay()}ms (barrier pause)")

                    // Additional barrier delay after each batch
                    delay(bidBackoffMechanism.getBarrierDelay())
                }
            } else {
                bidBackoffMechanism.notifySuccess()
            }
        }

        loadedBanner
    }

    /**
     * Trying to load the top rank (1) bid; load the next top one otherwise.
     */
    private suspend fun BidAdSourceResponse<SuspendableBanner>.loadOrDestroyBanner(): SuspendableBanner? = coroutineScope {
        var loadedBanner: SuspendableBanner? = null
        var winnerIndex: Int = -1

        val lossReasons = mutableMapOf<String, LossReason>()

        for ((index, bidItem) in bidItemsByRank.withIndex()) {
            ensureActive()

            val result = loadOrDestroyBanner(bidAdLoadTimeoutMillis, bidItem.createBidAd)
            val banner = result.banner

            if (banner != null) {
                loadedBanner = banner
                winnerIndex = index
                break
            } else {
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
                        CloudXLogger.debug(TAG, "Calling LURL for ${bidItem.adNetwork}, reason=${reason.name}, rank=${bidItem.rank}")

                        LossReporter.fireLoss(bidItem.lurl, reason)
                    }
                }
            }
        }

        loadedBanner
    }

    // returns: null - banner wasn't loaded.
    private suspend fun loadOrDestroyBanner(
        loadTimeoutMillis: Long,
        createBanner: suspend () -> SuspendableBanner
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

            isBannerLoaded = withTimeout(loadTimeoutMillis) { banner.load() }

        } catch (e: TimeoutCancellationException) {
            banner.timeout()
            return LoadResult(null, LossReason.TechnicalError)
        } catch (e: Exception) {
            return LoadResult(null, LossReason.TechnicalError)
        } finally {
            if (!isBannerLoaded) banner.destroy()
        }

        return LoadResult(banner, null) // No loss reason, we have a winner
    }

    private var currentBanner: SuspendableBanner? = null
    private var currentBannerEventHandlerJob: Job? = null

    private suspend fun showNewBanner(banner: SuspendableBanner) {
        listener?.onAdDisplayed(CloudXAd(banner.adNetwork))

        currentBanner = banner

        currentBannerEventHandlerJob?.cancel()
        currentBannerEventHandlerJob = scope.launch {
            launch {
                banner.event.filter { it == SuspendableBannerEvent.Click }.collect {
                    listener?.onAdClicked(CloudXAd(banner.adNetwork))
                }
            }
            launch {
                banner.lastErrorEvent.first { it != null }
                hideAndDestroyCurrentBanner()
                restartBannerRefresh()
            }
        }
    }

    private fun hideAndDestroyCurrentBanner() {
        currentBanner?.let { listener?.onAdHidden(CloudXAd(it.adNetwork)) }
        destroyCurrentBanner()
    }

    private fun destroyCurrentBanner() {
        currentBannerEventHandlerJob?.cancel()

        currentBanner?.destroy()
        currentBanner = null
    }

    override fun destroy() {
        scope.cancel()

        destroyCurrentBanner()
        bannerRefreshTimer.destroy()

        destroyBackupBanner()
        backupBannerLoadTimer.destroy()
    }
}

