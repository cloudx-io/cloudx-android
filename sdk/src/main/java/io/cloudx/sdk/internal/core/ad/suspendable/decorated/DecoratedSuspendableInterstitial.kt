package io.cloudx.sdk.internal.core.ad.suspendable.decorated

import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableInterstitial
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableInterstitialEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private typealias InterstitialFunc = (() -> Unit)?
private typealias ErrorInterstitialFunc = ((error: CloudXAdapterError) -> Unit)?
private typealias ClickInterstitialFunc = (() -> Unit)?

internal class DecoratedSuspendableInterstitial(
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
    private val interstitial: SuspendableInterstitial
) : SuspendableInterstitial by interstitial {

    private val scope = CoroutineScope(Dispatchers.Main).also {
        it.launch {
            event.collect { event ->
                when (event) {
                    SuspendableInterstitialEvent.Load -> onLoad?.invoke()
                    SuspendableInterstitialEvent.Show -> onShow?.invoke()
                    SuspendableInterstitialEvent.Impression -> onImpression?.invoke()
                    SuspendableInterstitialEvent.Skip -> onSkip?.invoke()
                    SuspendableInterstitialEvent.Complete -> onComplete?.invoke()
                    SuspendableInterstitialEvent.Hide -> onHide?.invoke()
                    is SuspendableInterstitialEvent.Click -> onClick?.invoke()
                    is SuspendableInterstitialEvent.Error -> onError?.invoke(event.error)
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