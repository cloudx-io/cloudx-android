package io.cloudx.sdk.internal.ads.fullscreen.interstitial

import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private typealias InterstitialFunc = (() -> Unit)?
private typealias ErrorInterstitialFunc = ((error: CloudXAdapterError) -> Unit)?
private typealias ClickInterstitialFunc = (() -> Unit)?

internal class DecoratedInterstitialAdapterDelegate(
    onLoad: InterstitialFunc = null,
    onShow: InterstitialFunc = null,
    onImpression: InterstitialFunc = null,
    onSkip: InterstitialFunc = null,
    onComplete: InterstitialFunc = null,
    onHide: InterstitialFunc = null,
    onClick: ClickInterstitialFunc = null,
    onError: ErrorInterstitialFunc = null,
    private val onDestroy: InterstitialFunc = null,
    private val onStartLoad: InterstitialFunc = null,
    private val onTimeout: InterstitialFunc = null,
    private val interstitial: InterstitialAdapterDelegate
) : InterstitialAdapterDelegate by interstitial {

    private val scope = CoroutineScope(Dispatchers.Main).also {
        it.launch {
            event.collect { event ->
                when (event) {
                    InterstitialAdapterDelegateEvent.Load -> onLoad?.invoke()
                    InterstitialAdapterDelegateEvent.Show -> onShow?.invoke()
                    InterstitialAdapterDelegateEvent.Impression -> onImpression?.invoke()
                    InterstitialAdapterDelegateEvent.Skip -> onSkip?.invoke()
                    InterstitialAdapterDelegateEvent.Complete -> onComplete?.invoke()
                    InterstitialAdapterDelegateEvent.Hide -> onHide?.invoke()
                    is InterstitialAdapterDelegateEvent.Click -> onClick?.invoke()
                    is InterstitialAdapterDelegateEvent.Error -> onError?.invoke(event.error)
                }
            }
        }
    }

    override suspend fun load(): Boolean {
        onStartLoad?.invoke()
        return interstitial.load()
    }

    override fun timeout() {
        onTimeout?.invoke()
    }

    override fun destroy() {
        onDestroy?.invoke()
        scope.cancel()
        interstitial.destroy()
    }
}