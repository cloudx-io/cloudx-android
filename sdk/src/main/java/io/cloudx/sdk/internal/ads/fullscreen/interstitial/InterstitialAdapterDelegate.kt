package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
import io.cloudx.sdk.internal.ads.fullscreen.FullscreenAdAdapterDelegate
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
 * Events emitted by InterstitialAdapterDelegate during its lifecycle
 */
sealed class InterstitialAdapterDelegateEvent {
    object Load : InterstitialAdapterDelegateEvent()
    object Show : InterstitialAdapterDelegateEvent()
    object Impression : InterstitialAdapterDelegateEvent()
    object Skip : InterstitialAdapterDelegateEvent()
    object Complete : InterstitialAdapterDelegateEvent()
    object Hide : InterstitialAdapterDelegateEvent()
    object Click : InterstitialAdapterDelegateEvent()
    data class Error(val error: CloudXError) : InterstitialAdapterDelegateEvent()
}

/**
 * A suspendable interstitial ad interface that provides lifecycle events and metadata
 */
// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.Interstitial with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface InterstitialAdapterDelegate :
    FullscreenAdAdapterDelegate<InterstitialAdapterDelegateEvent>

/**
 * Factory function to create a InterstitialAdapterDelegate instance
 */
internal fun InterstitialAdapterDelegate(
    placementName: String,
    placementId: String,
    adNetwork: AdNetwork,
    externalPlacementId: String?,
    price: Double,
    createInterstitial: (listener: CloudXInterstitialAdapterListener) -> CloudXInterstitialAdapter
): InterstitialAdapterDelegate =
    InterstitialAdapterDelegateImpl(
        placementName = placementName,
        placementId = placementId,
        bidderName = adNetwork.networkName,
        externalPlacementId = externalPlacementId,
        revenue = price,
        createInterstitial = createInterstitial
    )

/**
 * Implementation of InterstitialAdapterDelegate that wraps a CloudXInterstitialAdapter
 */
private class InterstitialAdapterDelegateImpl(
    override val placementName: String,
    override val placementId: String,
    override val bidderName: String,
    override val externalPlacementId: String?,
    override val revenue: Double,
    createInterstitial: (listener: CloudXInterstitialAdapterListener) -> CloudXInterstitialAdapter,
) : InterstitialAdapterDelegate {

    // State management
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _event = MutableSharedFlow<InterstitialAdapterDelegateEvent>()
    private val _lastErrorEvent = MutableStateFlow<CloudXError?>(null)

    override val event: SharedFlow<InterstitialAdapterDelegateEvent> = _event
    override val lastErrorEvent: StateFlow<CloudXError?> = _lastErrorEvent

    // Interstitial adapter with listener
    private val interstitial = createInterstitial(createAdapterListener())

    // Public API methods
    override val isAdLoadOperationAvailable: Boolean
        get() = interstitial.isAdLoadOperationAvailable

    override suspend fun load(): Boolean {
        val evtJob = scope.async {
            event.first {
                it is InterstitialAdapterDelegateEvent.Load || it is InterstitialAdapterDelegateEvent.Error
            }
        }

        interstitial.load()
        return evtJob.await() is InterstitialAdapterDelegateEvent.Load
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

    private fun createAdapterListener(): CloudXInterstitialAdapterListener {
        return object : CloudXInterstitialAdapterListener {
            override fun onLoad() {
                scope.launch { _event.emit(InterstitialAdapterDelegateEvent.Load) }
            }

            override fun onShow() {
                scope.launch { _event.emit(InterstitialAdapterDelegateEvent.Show) }
            }

            override fun onImpression() {
                scope.launch {
                    _event.emit(InterstitialAdapterDelegateEvent.Impression)
                }
            }

            override fun onSkip() {
                scope.launch { _event.emit(InterstitialAdapterDelegateEvent.Skip) }
            }

            override fun onComplete() {
                scope.launch { _event.emit(InterstitialAdapterDelegateEvent.Complete) }
            }

            override fun onHide() {
                scope.launch { _event.emit(InterstitialAdapterDelegateEvent.Hide) }
            }

            override fun onClick() {
                scope.launch { _event.emit(InterstitialAdapterDelegateEvent.Click) }
            }

            override fun onError(error: CloudXError) {
                scope.launch {
                    _event.emit(InterstitialAdapterDelegateEvent.Error(error))
                    _lastErrorEvent.value = error
                }
            }
        }
    }

}
