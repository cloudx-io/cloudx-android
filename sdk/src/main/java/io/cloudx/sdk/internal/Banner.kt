package io.cloudx.sdk.internal

import android.app.Activity
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXErrorCodes
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
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSource
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSourceResponse
import io.cloudx.sdk.internal.core.ad.source.bid.BidBannerSource
import io.cloudx.sdk.internal.core.ad.source.bid.BidSourceResult
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
        metricsTrackerNew = metricsTrackerNew,
        placementName = placementName
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
    private val placementName: String
) : Banner {

    private val TAG = "BannerImpl"

    private val scope = CoroutineScope(Dispatchers.Main)

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

    private suspend fun loadNewBanner(): SuspendableBanner = coroutineScope {
        var loadedBanner: SuspendableBanner? = null

        while (loadedBanner == null) {
            ensureActive()

            when (val res = bidAdSource.requestBid()){
                is BidSourceResult.Success -> {
                    loadedBanner = res.response.loadOrDestroyBanner()
                    if (loadedBanner == null) {
                        listener?.onAdLoadFailed(
                            CloudXAdError(
                                "No creative banners could be loaded for this bid.",
                                CloudXErrorCodes.NO_FILL
                            )
                        )
                        delay(refreshDelayMillis)
                    }
                }
                is BidSourceResult.Failure -> {
                    val err = res.error
                    // 1) Placement-breaking (permanent) errors: stop trying on this placement
                    if (err.isPermanent) {
                        listener?.onAdLoadFailed(
                            CloudXAdError(err.message, err.code)
                        )
                        // break out – nothing else to do
                        throw IllegalStateException("Permanent failure for placement: ${err.message}")
                    }
                    // 2) Traffic control sampled out (ADS_DISABLED): surface 308 once, then wait to next cycle
                    if (err.isTrafficControl) {
                        listener?.onAdLoadFailed(
                            CloudXAdError(err.message, err.code)
                        )
                        // delay until next refresh cycle
                        delay(refreshDelayMillis)
                        continue
                    }
                    // 3) Generic transient (5xx, timeout, network, parse, no-bid, etc.)
                    // V1 policy: do not spin; let refresh tick drive the next attempt
                    delay(refreshDelayMillis)
                }
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
                        println("📤 Sending resolved lurl for index=$index, adNetwork=${bidItem.adNetwork}, rank=${bidItem.rank}, reason=${reason.name}")
                        CloudXLogger.debug(TAG, "Calling LURL for ${bidItem.adNetwork}, reason=${reason.name}, rank=${bidItem.rank}")

                        LossReporter.fireLoss(bidItem.lurl, reason)
                    } else {
                        println("ℹ️ No lurl to send for index=$index, adNetwork=${bidItem.adNetwork}")
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
            println("⚠️ Failed to load banner: ${e.message}")
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

