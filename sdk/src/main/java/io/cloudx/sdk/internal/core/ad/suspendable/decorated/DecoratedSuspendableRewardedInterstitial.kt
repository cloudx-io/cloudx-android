package io.cloudx.sdk.internal.core.ad.suspendable.decorated

import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableRewardedInterstitial
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableRewardedInterstitialEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private typealias RewardedInterstitialFunc = (() -> Unit)?
private typealias ErrorRewardedInterstitialFunc = ((error: CloudXAdapterError) -> Unit)?
private typealias ClickRewardedInterstitialFunc = (() -> Unit)?

internal class DecoratedSuspendableRewardedInterstitial(
    onLoad: RewardedInterstitialFunc = null,
    onShow: RewardedInterstitialFunc = null,
    onImpression: RewardedInterstitialFunc = null,
    onReward: RewardedInterstitialFunc = null,
    onHide: RewardedInterstitialFunc = null,
    onClick: ClickRewardedInterstitialFunc = null,
    onError: ErrorRewardedInterstitialFunc = null,
    private val onDestroy: RewardedInterstitialFunc = null,
    private val onStartLoad: RewardedInterstitialFunc = null,
    private val onTimeout: RewardedInterstitialFunc = null,
    private val rewardedInterstitial: SuspendableRewardedInterstitial
) : SuspendableRewardedInterstitial by rewardedInterstitial {

    private val scope = CoroutineScope(Dispatchers.Main).also {
        it.launch {
            event.collect { event ->
                when (event) {
                    SuspendableRewardedInterstitialEvent.Load -> onLoad?.invoke()
                    SuspendableRewardedInterstitialEvent.Show -> onShow?.invoke()
                    SuspendableRewardedInterstitialEvent.Impression -> onImpression?.invoke()
                    SuspendableRewardedInterstitialEvent.Reward -> onReward?.invoke()
                    SuspendableRewardedInterstitialEvent.Hide -> onHide?.invoke()
                    is SuspendableRewardedInterstitialEvent.Click -> onClick?.invoke()
                    is SuspendableRewardedInterstitialEvent.Error -> onError?.invoke(event.error)
                    else -> {}
                }
            }
        }
    }

    override suspend fun load(): Boolean {
        onStartLoad?.invoke()
        return rewardedInterstitial.load()
    }

    override fun timeout() {
        onTimeout?.invoke()
    }

    override fun destroy() {
        onDestroy?.invoke()
        scope.cancel()
        rewardedInterstitial.destroy()
    }
}