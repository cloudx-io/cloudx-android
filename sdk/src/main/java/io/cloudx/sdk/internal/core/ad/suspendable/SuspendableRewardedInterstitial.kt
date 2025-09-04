package io.cloudx.sdk.internal.core.ad.suspendable

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
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
 * Events emitted by SuspendableRewardedInterstitial during its lifecycle
 */
sealed class SuspendableRewardedInterstitialEvent {
    object Load : SuspendableRewardedInterstitialEvent()
    object Show : SuspendableRewardedInterstitialEvent()
    object Impression : SuspendableRewardedInterstitialEvent()
    object Reward : SuspendableRewardedInterstitialEvent()
    object Hide : SuspendableRewardedInterstitialEvent()
    object Click : SuspendableRewardedInterstitialEvent()
    data class Error(val error: CloudXAdapterError) : SuspendableRewardedInterstitialEvent()
}

/**
 * A suspendable rewarded interstitial ad interface that provides lifecycle events and metadata
 */
// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.RewardedInterstitial with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface SuspendableRewardedInterstitial :
    SuspendableBaseFullscreenAd<SuspendableRewardedInterstitialEvent>

/**
 * Factory function to create a SuspendableRewardedInterstitial instance
 */
internal fun SuspendableRewardedInterstitial(
    placementName: String,
    placementId: String,
    adNetwork: AdNetwork,
    externalPlacementId: String?,
    price: Double?,
    createRewardedInterstitial: (listener: CloudXRewardedInterstitialAdapterListener) -> CloudXRewardedInterstitialAdapter
): SuspendableRewardedInterstitial =
    SuspendableRewardedInterstitialImpl(
        placementName = placementName,
        placementId = placementId,
        bidderName = adNetwork.networkName,
        externalPlacementId = externalPlacementId,
        revenue = price,
        createRewardedInterstitial = createRewardedInterstitial
    )

/**
 * Implementation of SuspendableRewardedInterstitial that wraps a CloudXRewardedInterstitialAdapter
 */
private class SuspendableRewardedInterstitialImpl(
    override val placementName: String,
    override val placementId: String,
    override val bidderName: String,
    override val externalPlacementId: String?,
    override val revenue: Double?,
    createRewardedInterstitial: (listener: CloudXRewardedInterstitialAdapterListener) -> CloudXRewardedInterstitialAdapter,
) : SuspendableRewardedInterstitial {

    // State management
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _event = MutableSharedFlow<SuspendableRewardedInterstitialEvent>()
    private val _lastErrorEvent = MutableStateFlow<CloudXAdapterError?>(null)

    override val event: SharedFlow<SuspendableRewardedInterstitialEvent> = _event
    override val lastErrorEvent: StateFlow<CloudXAdapterError?> = _lastErrorEvent

    // Rewarded interstitial adapter with listener
    private val rewardedInterstitial = createRewardedInterstitial(createAdapterListener())

    // Public API methods
    override val isAdLoadOperationAvailable: Boolean
        get() = rewardedInterstitial.isAdLoadOperationAvailable

    override suspend fun load(): Boolean {
        val evtJob = scope.async {
            event.first {
                it is SuspendableRewardedInterstitialEvent.Load || it is SuspendableRewardedInterstitialEvent.Error
            }
        }

        rewardedInterstitial.load()
        return evtJob.await() is SuspendableRewardedInterstitialEvent.Load
    }

    override fun show() {
        rewardedInterstitial.show()
    }

    override fun timeout() {
        // Currently unused - placeholder for future timeout handling
    }

    override fun destroy() {
        scope.cancel()
        rewardedInterstitial.destroy()
    }

    // Private helper methods
    private fun createAdapterListener(): CloudXRewardedInterstitialAdapterListener {
        return object : CloudXRewardedInterstitialAdapterListener {
            override fun onLoad() {
                scope.launch { _event.emit(SuspendableRewardedInterstitialEvent.Load) }
            }

            override fun onShow() {
                scope.launch { _event.emit(SuspendableRewardedInterstitialEvent.Show) }
            }

            override fun onImpression() {
                scope.launch { _event.emit(SuspendableRewardedInterstitialEvent.Impression) }
            }

            override fun onEligibleForReward() {
                scope.launch { _event.emit(SuspendableRewardedInterstitialEvent.Reward) }
            }

            override fun onHide() {
                scope.launch { _event.emit(SuspendableRewardedInterstitialEvent.Hide) }
            }

            override fun onClick() {
                scope.launch { _event.emit(SuspendableRewardedInterstitialEvent.Click) }
            }

            override fun onError(error: CloudXAdapterError) {
                scope.launch {
                    _event.emit(SuspendableRewardedInterstitialEvent.Error(error))
                    _lastErrorEvent.value = error
                }
            }
        }
    }
}