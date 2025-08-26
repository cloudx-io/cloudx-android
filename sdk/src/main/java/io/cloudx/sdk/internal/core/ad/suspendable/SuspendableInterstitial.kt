package io.cloudx.sdk.internal.core.ad.suspendable

import io.cloudx.sdk.internal.AdNetwork
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

// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.Interstitial with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface SuspendableInterstitial : SuspendableBaseFullscreenAd<SuspendableInterstitialEvent>

sealed class SuspendableInterstitialEvent {
    object Load : SuspendableInterstitialEvent()
    object Show : SuspendableInterstitialEvent()
    object Impression : SuspendableInterstitialEvent()
    object Skip : SuspendableInterstitialEvent()
    object Complete : SuspendableInterstitialEvent()
    object Hide : SuspendableInterstitialEvent()
    object Click : SuspendableInterstitialEvent()
    class Error(val error: io.cloudx.sdk.internal.adapter.CloudXAdapterError) : SuspendableInterstitialEvent()
}

internal fun SuspendableInterstitial(
    price: Double?,
    adNetwork: AdNetwork,
    adUnitId: String,
    createInterstitial: (listener: io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener) -> io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
): SuspendableInterstitial =
    SuspendableInterstitialImpl(price, adNetwork, adUnitId, createInterstitial)

private class SuspendableInterstitialImpl(
    override val price: Double?,
    override val adNetwork: AdNetwork,
    override val adUnitId: String,
    createInterstitial: (listener: io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener) -> io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter,
) : SuspendableInterstitial {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val interstitial = createInterstitial(object :
        io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener {
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

        override fun onError(error: io.cloudx.sdk.internal.adapter.CloudXAdapterError) {
            scope.launch {
                _event.emit(SuspendableInterstitialEvent.Error(error))
                // 1 liner instead of _event.collect { /*assign error*/ }
                _lastErrorEvent.value = error
            }
        }
    })

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

    override fun timeout() {
        // unused
    }

    override fun show() {
        interstitial.show()
    }

    private val _event = MutableSharedFlow<SuspendableInterstitialEvent>()
    override val event: SharedFlow<SuspendableInterstitialEvent> = _event

    private val _lastErrorEvent = MutableStateFlow<io.cloudx.sdk.internal.adapter.CloudXAdapterError?>(null)
    override val lastErrorEvent: StateFlow<io.cloudx.sdk.internal.adapter.CloudXAdapterError?> = _lastErrorEvent

    override fun destroy() {
        scope.cancel()
        interstitial.destroy()
    }
}