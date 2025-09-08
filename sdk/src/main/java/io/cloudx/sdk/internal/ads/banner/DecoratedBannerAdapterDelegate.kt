package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private typealias BannerFunc = (() -> Unit)?
private typealias ErrorBannerFunc = ((error: CloudXAdapterError) -> Unit)?

internal class DecoratedBannerAdapterDelegate(
    onLoad: BannerFunc = null,
    onShow: BannerFunc = null,
    onImpression: BannerFunc = null,
    onClick: BannerFunc = null,
    onError: ErrorBannerFunc = null,
    private val onDestroy: BannerFunc = null,
    private val onStartLoad: BannerFunc = null,
    private val onTimeout: BannerFunc = null,
    private val banner: BannerAdapterDelegate
) : BannerAdapterDelegate by banner {

    private val scope = CoroutineScope(Dispatchers.Main).also {
        it.launch {
            event.collect { event ->
                when (event) {
                    BannerAdapterDelegateEvent.Load -> onLoad?.invoke()
                    BannerAdapterDelegateEvent.Show -> onShow?.invoke()
                    BannerAdapterDelegateEvent.Impression -> onImpression?.invoke()
                    BannerAdapterDelegateEvent.Click -> onClick?.invoke()
                    is BannerAdapterDelegateEvent.Error -> onError?.invoke(event.error)
                }
            }
        }
    }

    override suspend fun load(): Boolean {
        onStartLoad?.invoke()
        return banner.load()
    }

    override fun timeout() {
        onTimeout?.invoke()
    }

    override fun destroy() {
        onDestroy?.invoke()
        scope.cancel()
        banner.destroy()
    }
}