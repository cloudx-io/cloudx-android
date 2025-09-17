package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialAdapterListener
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
 * Events emitted by RewardedInterstitialAdapterDelegate during its lifecycle
 */
sealed class RewardedInterstitialAdapterDelegateEvent {
    object Load : RewardedInterstitialAdapterDelegateEvent()
    object Show : RewardedInterstitialAdapterDelegateEvent()
    object Impression : RewardedInterstitialAdapterDelegateEvent()
    object Reward : RewardedInterstitialAdapterDelegateEvent()
    object Hide : RewardedInterstitialAdapterDelegateEvent()
    object Click : RewardedInterstitialAdapterDelegateEvent()
    data class Error(val error: CloudXAdapterError) : RewardedInterstitialAdapterDelegateEvent()
}

/**
 * A suspendable rewarded interstitial ad interface that provides lifecycle events and metadata
 */
// TODO. Some methods/inits can be reused for any ad type (destroy() etc).
// TODO. Replace sdk.adapter.RewardedInterstitial with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface RewardedInterstitialAdapterDelegate :
    FullscreenAdAdapterDelegate<RewardedInterstitialAdapterDelegateEvent>

/**
 * Factory function to create a RewardedInterstitialAdapterDelegate instance
 */
internal fun RewardedInterstitialAdapterDelegate(
    placementName: String,
    placementId: String,
    adNetwork: AdNetwork,
    externalPlacementId: String?,
    price: Double?,
    createRewardedInterstitial: (listener: CloudXRewardedInterstitialAdapterListener) -> CloudXRewardedInterstitialAdapter
): RewardedInterstitialAdapterDelegate =
    RewardedInterstitialAdapterDelegateImpl(
        placementName = placementName,
        placementId = placementId,
        bidderName = adNetwork.networkName,
        externalPlacementId = externalPlacementId,
        revenue = price,
        createRewardedInterstitial = createRewardedInterstitial
    )

/**
 * Implementation of RewardedInterstitialAdapterDelegate that wraps a CloudXRewardedInterstitialAdapter
 */
private class RewardedInterstitialAdapterDelegateImpl(
    override val placementName: String,
    override val placementId: String,
    override val bidderName: String,
    override val externalPlacementId: String?,
    override val revenue: Double?,
    createRewardedInterstitial: (listener: CloudXRewardedInterstitialAdapterListener) -> CloudXRewardedInterstitialAdapter,
) : RewardedInterstitialAdapterDelegate {

    // State management
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _event = MutableSharedFlow<RewardedInterstitialAdapterDelegateEvent>()
    private val _lastErrorEvent = MutableStateFlow<CloudXAdapterError?>(null)

    override val event: SharedFlow<RewardedInterstitialAdapterDelegateEvent> = _event
    override val lastErrorEvent: StateFlow<CloudXAdapterError?> = _lastErrorEvent

    // Rewarded interstitial adapter with listener
    private val rewardedInterstitial = createRewardedInterstitial(createAdapterListener())

    // Public API methods
    override val isAdLoadOperationAvailable: Boolean
        get() = rewardedInterstitial.isAdLoadOperationAvailable

    override suspend fun load(): Boolean {
        val evtJob = scope.async {
            event.first {
                it is RewardedInterstitialAdapterDelegateEvent.Load || it is RewardedInterstitialAdapterDelegateEvent.Error
            }
        }

        rewardedInterstitial.load()
        return evtJob.await() is RewardedInterstitialAdapterDelegateEvent.Load
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

    private fun createAdapterListener(): CloudXRewardedInterstitialAdapterListener {
        return object : CloudXRewardedInterstitialAdapterListener {
            override fun onLoad() {
                scope.launch { _event.emit(RewardedInterstitialAdapterDelegateEvent.Load) }
            }

            override fun onShow() {
                scope.launch { _event.emit(RewardedInterstitialAdapterDelegateEvent.Show) }
            }

            override fun onImpression() {
                scope.launch { _event.emit(RewardedInterstitialAdapterDelegateEvent.Impression) }
            }

            override fun onEligibleForReward() {
                scope.launch { _event.emit(RewardedInterstitialAdapterDelegateEvent.Reward) }
            }

            override fun onHide() {
                scope.launch { _event.emit(RewardedInterstitialAdapterDelegateEvent.Hide) }
            }

            override fun onClick() {
                scope.launch { _event.emit(RewardedInterstitialAdapterDelegateEvent.Click) }
            }

            override fun onError(error: CloudXAdapterError) {
                scope.launch {
                    _event.emit(RewardedInterstitialAdapterDelegateEvent.Error(error))
                    _lastErrorEvent.value = error
                }
            }
        }
    }

}
