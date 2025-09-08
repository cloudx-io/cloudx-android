package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.internal.ads.AdTimeoutEvent
import io.cloudx.sdk.internal.ads.LastErrorEvent
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
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
    data class Error(val error: CloudXAdapterError) : BannerAdapterDelegateEvent()
}

/**
 * A suspendable banner ad interface that provides lifecycle events and metadata
 */
// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.Banner with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface BannerAdapterDelegate : AdTimeoutEvent, LastErrorEvent, Destroyable, CloudXAd {
    suspend fun load(): Boolean
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
    nurl: String?,
    lurl: String?,
    createBanner: (listener: CloudXAdViewAdapterListener) -> CloudXAdViewAdapter
): BannerAdapterDelegate =
    BannerAdapterDelegateImpl(
        placementName = placementName,
        placementId = placementId,
        bidderName = adNetwork.networkName,
        externalPlacementId = externalPlacementId,
        revenue = price,
        nurl = nurl,
        lurl = lurl,
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
    private val nurl: String?,
    private val lurl: String?,
    createBanner: (listener: CloudXAdViewAdapterListener) -> CloudXAdViewAdapter,
) : BannerAdapterDelegate {

    // State management
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _event = MutableSharedFlow<BannerAdapterDelegateEvent>()
    private val _lastErrorEvent = MutableStateFlow<CloudXAdapterError?>(null)

    override val event: SharedFlow<BannerAdapterDelegateEvent> = _event
    override val lastErrorEvent: StateFlow<CloudXAdapterError?> = _lastErrorEvent

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
                    handleImpressionTracking()
                }
            }

            override fun onClick() {
                scope.launch { _event.emit(BannerAdapterDelegateEvent.Click) }
            }

            override fun onError(error: CloudXAdapterError) {
                scope.launch {
                    _event.emit(BannerAdapterDelegateEvent.Error(error))
                    _lastErrorEvent.value = error
                }
            }
        }
    }

    private fun handleImpressionTracking() {
        // TODO: Make a separate class for nurls and burls to handle. It looks ugly here.
        nurl?.let { url ->
            val completeUrl = url.replace("\${AUCTION_PRICE}", revenue?.toString() ?: "")
            scope.launch(Dispatchers.IO) {
                try {
                    val response: HttpResponse = CloudXHttpClient().get(completeUrl)
                    if (!response.status.isSuccess()) {
                        // TODO: Add proper logging when available
                        // CloudXLogger.error("BannerImpl", "Failed to call nurl status: ${response.status}")
                    }
                } catch (e: Exception) {
                    // TODO: Add proper logging when available
                    // CloudXLogger.error("BannerImpl", "Error calling nurl error: ${e.message}")
                }
            }
        }
    }
}