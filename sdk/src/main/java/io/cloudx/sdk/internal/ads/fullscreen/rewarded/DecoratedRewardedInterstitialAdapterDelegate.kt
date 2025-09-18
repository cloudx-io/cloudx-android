package io.cloudx.sdk.internal.ads.fullscreen.rewarded

import io.cloudx.sdk.CloudXError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private typealias RewardedInterstitialFunc = (() -> Unit)?
private typealias ErrorRewardedInterstitialFunc = ((error: CloudXError) -> Unit)?
private typealias ClickRewardedInterstitialFunc = (() -> Unit)?

internal class DecoratedRewardedInterstitialAdapterDelegate(
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
    private val rewardedInterstitial: RewardedInterstitialAdapterDelegate
) : RewardedInterstitialAdapterDelegate by rewardedInterstitial {

    private val scope = CoroutineScope(Dispatchers.Main).also {
        it.launch {
            event.collect { event ->
                when (event) {
                    RewardedInterstitialAdapterDelegateEvent.Load -> onLoad?.invoke()
                    RewardedInterstitialAdapterDelegateEvent.Show -> onShow?.invoke()
                    RewardedInterstitialAdapterDelegateEvent.Impression -> onImpression?.invoke()
                    RewardedInterstitialAdapterDelegateEvent.Reward -> onReward?.invoke()
                    RewardedInterstitialAdapterDelegateEvent.Hide -> onHide?.invoke()
                    is RewardedInterstitialAdapterDelegateEvent.Click -> onClick?.invoke()
                    is RewardedInterstitialAdapterDelegateEvent.Error -> onError?.invoke(event.error)
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