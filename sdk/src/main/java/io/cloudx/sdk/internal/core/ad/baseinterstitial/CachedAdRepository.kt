package io.cloudx.sdk.internal.core.ad.baseinterstitial

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.bid.LossReporter
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.ad.AdMetaData
import io.cloudx.sdk.internal.core.ad.source.bid.BidAdSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class CachedAdRepository<SuspendableAd: Destroyable, C: CacheableAd>(
    private val bidAdSource: BidAdSource<SuspendableAd>,
    cacheSize: Int,
    preCachedAdLifetimeMinutes: Int,
    bidMaxBackOffTimeMillis: Long,
    private val bidLoadTimeoutMillis: Long,
    private val createCacheableAd: suspend (SuspendableAd) -> C,
    connectionStatusService: ConnectionStatusService,
    private val appLifecycleService: AppLifecycleService,
    placementType: AdType,
): Destroyable {

    private val TAG = "Bid$placementType"

    private val scope = CoroutineScope(Dispatchers.Main)
//    private val bidBackoffAlgorithm = BidBackoffAlgorithm(bidMaxBackOffTimeMillis)

    private val cachedQueue = CachedAdQueue<C>(
        cacheSize,
        preCachedAdLifetimeMinutes,
        connectionStatusService,
        appLifecycleService,
        placementType
    )

    val hasAds: StateFlow<Boolean> = cachedQueue.hasItems

    val topAdMetaData: AdMetaData? get() = cachedQueue.topAdMeta

    fun popAd(): C? = cachedQueue.popAd()

    init {
        // TODO. Move to CachedQueue?
        scope.launch {
            // Bid ads try to fill the remaining space in the cached queue.
            while (true) {
                ensureActive()

                when (enqueueBid()) {
                    AdLoadOperationStatus.AdLoadSuccess -> {
//                        bidBackoffAlgorithm.notifyAdLoadSuccess()
                    }

                    AdLoadOperationStatus.AdLoadOperationUnavailable -> {
                        //TODO this is a workaround. Should be improved.
                        delay(1000)
                    }

                    else -> {
//                        bidBackoffAlgorithm.notifyAdLoadFailed()
//                        if (bidBackoffAlgorithm.isThreshold) {
//                            val delayMillis = bidBackoffAlgorithm.calculateDelayMillis()
//                            CloudXLogger.info(
//                                TAG,
//                                "delaying for: ${delayMillis}ms as ${bidBackoffAlgorithm.bidFails} bid responses have failed to load"
//                            )
//                            delay(delayMillis)
//                        }
                    }
                }
            }
        }
    }

    private suspend fun enqueueBid(): AdLoadOperationStatus = coroutineScope {
        appLifecycleService.awaitAppResume()
        cachedQueue.hasAvailableSlots.first { it }

        val bidResponse = bidAdSource.requestBid() ?: return@coroutineScope AdLoadOperationStatus.AdLoadFailed

//        for ((index, bidItem) in bidResponse.bidItemsByRank.withIndex()) {
//            ensureActive()
//
//            val ad = runCatching {
//                withTimeoutOrNull(bidLoadTimeoutMillis) {
//                    createCacheableAd(bidItem.createBidAd())
//                }
//            }.getOrNull()
//
//            if (ad != null) {
//                val result = cachedQueue.enqueueBidAd(
//                    bidItem.price,
//                    bidLoadTimeoutMillis,
//                    createBidAd = { ad }
//                )
//
//                if (result == AdLoadOperationStatus.AdLoadSuccess) {
//                    // Fire LossReason.LostToHigherBid for all lower-ranked bids
//                    val lowerRanked = bidResponse.bidItemsByRank.drop(index + 1)
//                    for (loserItem in lowerRanked) {
//                        LossReporter.fireLoss(
//                            loserItem.lurl,
//                            LossReason.LostToHigherBid
//                        )
//                    }
//
//                    return@coroutineScope result
//                } else {
//                    // Ad created but failed to enqueue — technical error
//                    LossReporter.fireLoss(bidItem.lurl, LossReason.TechnicalError)
//                }
//            } else {
//                // Ad failed to create — technical error
//                LossReporter.fireLoss(bidItem.lurl, LossReason.TechnicalError)
//            }
//        }

        AdLoadOperationStatus.AdLoadFailed
    }

    override fun destroy() {
        scope.cancel()
        cachedQueue.destroy()
    }
}