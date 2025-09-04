package io.cloudx.sdk.internal.core.ad.suspendable

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
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
 * Events emitted by SuspendableInterstitial during its lifecycle
 */
sealed class SuspendableInterstitialEvent {
    object Load : SuspendableInterstitialEvent()
    object Show : SuspendableInterstitialEvent()
    object Impression : SuspendableInterstitialEvent()
    object Skip : SuspendableInterstitialEvent()
    object Complete : SuspendableInterstitialEvent()
    object Hide : SuspendableInterstitialEvent()
    object Click : SuspendableInterstitialEvent()
    data class Error(val error: CloudXAdapterError) : SuspendableInterstitialEvent()
}

/**
 * A suspendable interstitial ad interface that provides lifecycle events and metadata
 */
// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.Interstitial with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface SuspendableInterstitial :
    SuspendableBaseFullscreenAd<SuspendableInterstitialEvent>

/**
 * Factory function to create a SuspendableInterstitial instance
 */
internal fun SuspendableInterstitial(
    placementName: String,
    placementId: String,
    adNetwork: AdNetwork,
    externalPlacementId: String?,
    price: Double?,
    createInterstitial: (listener: CloudXInterstitialAdapterListener) -> CloudXInterstitialAdapter
): SuspendableInterstitial =
    SuspendableInterstitialImpl(
        placementName = placementName,
        placementId = placementId,
        bidderName = adNetwork.networkName,
        externalPlacementId = externalPlacementId,
        revenue = price,
        createInterstitial = createInterstitial
    )

/**
 * Implementation of SuspendableInterstitial that wraps a CloudXInterstitialAdapter
 */
private class SuspendableInterstitialImpl(
    override val placementName: String,
    override val placementId: String,
    override val bidderName: String,
    override val externalPlacementId: String?,
    override val revenue: Double?,
    createInterstitial: (listener: CloudXInterstitialAdapterListener) -> CloudXInterstitialAdapter,
) : SuspendableInterstitial {

    // State management
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _event = MutableSharedFlow<SuspendableInterstitialEvent>()
    private val _lastErrorEvent = MutableStateFlow<CloudXAdapterError?>(null)

    override val event: SharedFlow<SuspendableInterstitialEvent> = _event
    override val lastErrorEvent: StateFlow<CloudXAdapterError?> = _lastErrorEvent

    // Interstitial adapter with listener
    private val interstitial = createInterstitial(createAdapterListener())

    // Public API methods
    override val isAdLoadOperationAvailable: Boolean
        get() = interstitial.isAdLoadOperationAvailable

    override suspend fun load(): Boolean {
        val evtJob = scope.async {
            event.first {
                it is SuspendableInterstitialEvent.Load || it is SuspendableInterstitialEvent.Error
            }
        }

        interstitial.load()
        return evtJob.await() is SuspendableInterstitialEvent.Load
    }

    override fun show() {
        interstitial.show()
    }

    override fun timeout() {
        // Currently unused - placeholder for future timeout handling
    }

    override fun destroy() {
        scope.cancel()
        interstitial.destroy()
    }

    // Private helper methods
    private fun createAdapterListener(): CloudXInterstitialAdapterListener {
        return object : CloudXInterstitialAdapterListener {
            override fun onLoad() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Load) }
            }

            override fun onShow() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Show) }
            }

            override fun onImpression() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Impression) }
            }

            override fun onSkip() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Skip) }
            }

            override fun onComplete() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Complete) }
            }

            override fun onHide() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Hide) }
            }

            override fun onClick() {
                scope.launch { _event.emit(SuspendableInterstitialEvent.Click) }
            }

            override fun onError(error: CloudXAdapterError) {
                scope.launch {
                    _event.emit(SuspendableInterstitialEvent.Error(error))
                    _lastErrorEvent.value = error
                }
            }
        }
    }
}