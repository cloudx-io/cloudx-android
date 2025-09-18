package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.ads.CXAdapterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Events emitted by BannerAdapterDelegate during its lifecycle
 */
sealed class BannerAdapterDelegateEvent {
    object Load : BannerAdapterDelegateEvent()
    object Show : BannerAdapterDelegateEvent()
    object Impression : BannerAdapterDelegateEvent()
    object Click : BannerAdapterDelegateEvent()
    data class Error(val error: CloudXError) : BannerAdapterDelegateEvent()
}

/**
 * A suspendable banner ad interface that provides lifecycle events and metadata
 */
// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.Banner with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface BannerAdapterDelegate : CXAdapterDelegate {
    val event: SharedFlow<BannerAdapterDelegateEvent>
}

/**
 * Factory function to create a BannerAdapterDelegate instance
 */
internal fun BannerAdapterDelegate(
    placementName: String,
    placementId: String,
    adNetwork: AdNetwork,
    externalPlacementId: String?,
    price: Double?,
    createBanner: (listener: CloudXAdViewAdapterListener) -> CloudXAdViewAdapter
): BannerAdapterDelegate =
    BannerAdapterDelegateImpl(
        placementName = placementName,
        placementId = placementId,
        bidderName = adNetwork.networkName,
        externalPlacementId = externalPlacementId,
        revenue = price,
        createBanner = createBanner
    )

/**
 * Implementation of BannerAdapterDelegate that wraps a CloudXAdViewAdapter
 */
private class BannerAdapterDelegateImpl(
    override val placementName: String,
    override val placementId: String,
    override val bidderName: String,
    override val externalPlacementId: String?,
    override val revenue: Double?,
    createBanner: (listener: CloudXAdViewAdapterListener) -> CloudXAdViewAdapter,
) : BannerAdapterDelegate {

    // State management
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _event = MutableSharedFlow<BannerAdapterDelegateEvent>()
    private val _lastErrorEvent = MutableStateFlow<CloudXError?>(null)

    override val event: SharedFlow<BannerAdapterDelegateEvent> = _event
    override val lastErrorEvent: StateFlow<CloudXError?> = _lastErrorEvent

    // Banner adapter with listener
    private val banner = createBanner(createAdapterListener())

    // Public API methods
    override suspend fun load(): Boolean {
        val evtJob = scope.async {
            event.first {
                it is BannerAdapterDelegateEvent.Load || it is BannerAdapterDelegateEvent.Error
            }
        }

        banner.load()
        return evtJob.await() is BannerAdapterDelegateEvent.Load
    }

    override fun timeout() {
        // Currently unused - placeholder for future timeout handling
    }

    override fun destroy() {
        scope.cancel()
        banner.destroy()
    }

    // Private helper methods
    private fun createAdapterListener(): CloudXAdViewAdapterListener {
        return object : CloudXAdViewAdapterListener {
            override fun onLoad() {
                scope.launch { _event.emit(BannerAdapterDelegateEvent.Load) }
            }

            override fun onShow() {
                scope.launch { _event.emit(BannerAdapterDelegateEvent.Show) }
            }

            override fun onImpression() {
                scope.launch {
                    _event.emit(BannerAdapterDelegateEvent.Impression)
                }
            }

            override fun onClick() {
                scope.launch { _event.emit(BannerAdapterDelegateEvent.Click) }
            }

            override fun onError(error: CloudXError) {
                scope.launch {
                    _event.emit(BannerAdapterDelegateEvent.Error(error))
                    _lastErrorEvent.value = error
                }
            }
        }
    }

}
