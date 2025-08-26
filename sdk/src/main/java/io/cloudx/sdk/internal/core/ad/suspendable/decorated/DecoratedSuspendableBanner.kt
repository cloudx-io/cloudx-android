package io.cloudx.sdk.internal.core.ad.suspendable.decorated

import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableBanner
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableBannerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private typealias BannerFunc = (() -> Unit)?
private typealias ErrorBannerFunc = ((error: CloudXAdapterError) -> Unit)?

internal class DecoratedSuspendableBanner(
    onLoad: BannerFunc = null,
    onShow: BannerFunc = null,
    onImpression: BannerFunc = null,
    onClick: BannerFunc = null,
    onError: ErrorBannerFunc = null,
    private val onDestroy: BannerFunc = null,
    private val onStartLoad: BannerFunc = null,
    private val onTimeout: BannerFunc = null,
    private val banner: SuspendableBanner
) : SuspendableBanner by banner {

    private val scope = CoroutineScope(Dispatchers.Main).also {
        it.launch {
            event.collect { event ->
                when (event) {
                    SuspendableBannerEvent.Load -> onLoad?.invoke()
                    SuspendableBannerEvent.Show -> onShow?.invoke()
                    SuspendableBannerEvent.Impression -> onImpression?.invoke()
                    SuspendableBannerEvent.Click -> onClick?.invoke()
                    is SuspendableBannerEvent.Error -> onError?.invoke(event.error)
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