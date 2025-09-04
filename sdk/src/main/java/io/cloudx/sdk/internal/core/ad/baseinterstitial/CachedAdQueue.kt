package io.cloudx.sdk.internal.core.ad.baseinterstitial

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.internal.core.ad.adapter_delegate.AdTimeoutEvent
import io.cloudx.sdk.internal.core.ad.adapter_delegate.LastErrorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.max

// Note: main-thread, non-sync implementation.
internal class CachedAdQueue<T : CacheableAd>(
    private val maxCapacity: Int,
    private val cachedAdLifetimeMinutes: Int,
    private val connectionStatusService: ConnectionStatusService,
    private val appLifecycleService: AppLifecycleService,
    placementType: AdType,
) : Destroyable {

    private val TAG = "Bid$placementType"

    private val scope = CoroutineScope(Dispatchers.Main)

    private val sortedQueue = mutableListOf<QueueItem<T>>()
    private val invalidQueueItemHandlers = mutableMapOf<QueueItem<T>, Job>()

    private val availableSlots = MutableStateFlow(sortedQueue.size)

    private fun recalculateAvailableAdSlots() {
        val currentMaxCapacity = if (isMeteredConnection) 1 else maxCapacity
        availableSlots.value = max(0, currentMaxCapacity - sortedQueue.size)

        Logger.d(TAG, "ads in queue: ${sortedQueue.size}")
    }

    val topCloudXAd: CloudXAd? get() = sortedQueue.firstOrNull()?.ad

    val hasItems: StateFlow<Boolean> = availableSlots
        .map { sortedQueue.size != 0 }
        // TODO. Switch to WhileSubscribed?
        .stateIn(scope, SharingStarted.Eagerly, false)

    val hasAvailableSlots: Flow<Boolean> = availableSlots.map { it > 0 }

    private var isMeteredConnection: Boolean = false
        set(value) {
            field = value
            recalculateAvailableAdSlots()
        }

    init {
        scope.launch {
            connectionStatusService.currentConnectionInfoEvent.collect {
                isMeteredConnection = it == null || it.isMetered
            }
        }
    }

    // Fill cache with bid ad until the cache queue is full. Whenever not full - top it off.
    suspend fun enqueueBidAd(
        price: Double,
        loadTimeoutMillis: Long,
        createBidAd: suspend () -> T
    ): AdLoadOperationStatus {
        /// TODO. remove await stuff since parallel method call support is unnecessary for now.
        // Delays enqueue operations to prevent cache going out of bounds
        // (isMeteredConnection case isn't handled here - that's completely fine).
        // That's enough for MAIN THREAD ONLY calls.
        while (enqueueBidJobCount.value >= availableSlots.value) {
            enqueueBidJobCount.combine(availableSlots) { jobCount, availableSlots ->
                availableSlots - jobCount
            }.first {
                it > 0
            }
        }

        val enqueueResult = scope.async {
            // TODO. Replace runCatching with actual check whether ad can be created based on parameters
            //  For instance:
            //  Create AdColony ad when there's no AdColony ad in this module or init parameters are invalid.
            val ad = try {
                createBidAd()
            } catch (e: Exception) {
                null
            }
                ?: return@async AdLoadOperationStatus.AdLoadFailed //todo should we sent AdLoadOperationStatus.AdLoadError here?

            val adLoadOperationStatus = ad.loadOrDestroyAd(loadTimeoutMillis)
            if (adLoadOperationStatus == AdLoadOperationStatus.AdLoadSuccess) {
                addQueueItem(QueueItem(ad, price))
            }
            adLoadOperationStatus
        }

        return try {
            enqueueBidJobCount.value++
            enqueueResult.await()
        } finally {
            enqueueBidJobCount.value--
        }
    }

    private val enqueueBidJobCount = MutableStateFlow(0)

    // true - loaded, false - destroyed
    // finally block takes care of a leaked ad by destroying it in case job is cancelled/timeout.
    private suspend fun CacheableAd.loadOrDestroyAd(loadTimeoutMillis: Long): AdLoadOperationStatus {
        var adLoadOperationStatus: AdLoadOperationStatus = AdLoadOperationStatus.AdLoadFailed

        try {
            // TODO. Duplicate. Refactor.
            //  Await both via flow.combine or alternatives.
            appLifecycleService.awaitAppResume()
            connectionStatusService.awaitConnection()

            if (!isAdLoadOperationAvailable) {
                adLoadOperationStatus = AdLoadOperationStatus.AdLoadOperationUnavailable
            } else if (withTimeout(loadTimeoutMillis) {
                    load()
                }) {
                adLoadOperationStatus = AdLoadOperationStatus.AdLoadSuccess
            }
        } catch (e: TimeoutCancellationException) {
            timeout()
            adLoadOperationStatus = AdLoadOperationStatus.AdLoadTimeout
        } finally {
            if (adLoadOperationStatus != AdLoadOperationStatus.AdLoadSuccess) destroy()
        }

        return adLoadOperationStatus
    }

    private fun addQueueItem(item: QueueItem<T>) {
        with(sortedQueue) {
            add(item)
            sort()
        }

        invalidQueueItemHandlers[item] = scope.launch {
            // Life time is over -> remove/destroy.
            launch {
                delay(cachedAdLifetimeMinutes * 1000 * 60L)
                removeQueueItem(item)
            }
            // Any ad error -> remove/destroy.
            launch {
                item.ad.lastErrorEvent.first { it != null }
                removeQueueItem(item)
            }
        }

        // TODO. Refactor. Implement onSizeChangedEvent or something for the queue.
        recalculateAvailableAdSlots()
    }

    private fun removeQueueItem(item: QueueItem<T>?) {
        if (item == null) return

        detachInvalidQueueItemHandlers(item)

        // TODO. Refactor. Works, not that ugly though not ideal.
        // Destroy ad only in case if the item is still managed by the cache object (this).
        // Effectively fixes the case (look fun enqueueBidAd()):
        // 1. itemToRemoveFromQueue is in closure and it happens to be the bid ad.
        // 2. Trying to load a new bid ad with better price/whatever in order to replace the old one from the step 1.
        // 3. Task suspends: waiting for new bid ad load result; note that itemToRemoveFromQueue is in closure.
        // 4. Publisher decides to display the ad: that means popAd() fun call.
        // If the itemToRemoveFromQueue is at the top of the queue: that means Publisher gets the itemToRemoveFromQueue ad.
        // 5. Ad from itemToRemoveFromQueue starts playing.
        // 6. New bid ad is loaded successfully. Trying to replace the itemToRemoveFromQueue with the new one.
        // But wait, that means the itemToRemoveFromQueue is already being displayed by The publisher,
        // and here we are, trying to destroy a thing which doesn't belong to the cache anymore.
        // Let's do a check if the itemToRemoveFromQueue still belongs to the sortedQueue.
        if (sortedQueue.remove(item)) {
            item.ad.destroy()
        }

        // TODO. Refactor. Implement onSizeChangedEvent or something for the queue.
        recalculateAvailableAdSlots()
    }

    fun popAd(): T? {
        if (sortedQueue.isEmpty()) return null

        val item = sortedQueue.removeAt(0)

        detachInvalidQueueItemHandlers(item)

        // TODO. Refactor. Implement onSizeChangedEvent or something for the queue.
        recalculateAvailableAdSlots()

        return item.ad
    }

    private fun detachInvalidQueueItemHandlers(item: QueueItem<T>?) {
        invalidQueueItemHandlers[item]?.cancel()
        invalidQueueItemHandlers.remove(item)
    }

    override fun destroy() {
        scope.cancel()
        // Not necessary. Just for the sake of peace of mind.
        invalidQueueItemHandlers.clear()

        sortedQueue.forEach { it.ad.destroy() }
        // Not necessary. Just for the sake of peace of mind.
        sortedQueue.clear()
    }
}

internal interface CacheableAd : AdTimeoutEvent, CloudXAdLoadOperationAvailability, LastErrorEvent,
    Destroyable, CloudXAd {

    suspend fun load(): Boolean
}

private class QueueItem<T : CacheableAd>(
    val ad: T,
    val price: Double,
) : Comparable<QueueItem<T>> {

    override fun compareTo(other: QueueItem<T>): Int =
        -price.compareTo(other.price)
}

internal enum class AdLoadOperationStatus { AdLoadSuccess, AdLoadFailed, AdLoadTimeout, AdLoadOperationUnavailable }