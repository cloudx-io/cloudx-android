package io.cloudx.sdk.internal.core.ad.suspendable

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.core.ad.AdMetaData
import io.cloudx.sdk.internal.httpclient.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
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
// TODO. Replace sdk.adapter.Banner with this?
// TODO. Merge with DecoratedSuspendableXXXX?
internal interface SuspendableBanner: AdTimeoutEvent, LastErrorEvent, Destroyable, AdMetaData {

    suspend fun load(): Boolean
    val event: SharedFlow<SuspendableBannerEvent>
}

sealed class SuspendableBannerEvent {
    object Load: SuspendableBannerEvent()
    object Show: SuspendableBannerEvent()
    object Impression: SuspendableBannerEvent()
    object Click: SuspendableBannerEvent()
    class Error(val error: io.cloudx.sdk.internal.adapter.CloudXAdError): SuspendableBannerEvent()
}

internal fun SuspendableBanner(
    price: Double?,
    adNetwork: AdNetwork,
    adUnitId: String,
    nurl: String?,
    createBanner: (listener: io.cloudx.sdk.internal.adapter.BannerListener) -> io.cloudx.sdk.internal.adapter.Banner
): SuspendableBanner =
    SuspendableBannerImpl(price, adNetwork, adUnitId, nurl, createBanner)

private class SuspendableBannerImpl(
    override val price: Double?,
    override val adNetwork: AdNetwork,
    override val adUnitId: String,
    private val nurl: String?,
    createBanner: (listener: io.cloudx.sdk.internal.adapter.BannerListener) -> io.cloudx.sdk.internal.adapter.Banner,
): SuspendableBanner {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val banner = createBanner(object: io.cloudx.sdk.internal.adapter.BannerListener {
        override fun onLoad() {
            scope.launch { _event.emit(SuspendableBannerEvent.Load) }
        }

        override fun onShow() {
            scope.launch { _event.emit(SuspendableBannerEvent.Show) }
        }

        override fun onImpression() {
            scope.launch {
                _event.emit(SuspendableBannerEvent.Impression)

                // TODO: Make a separate class for nurls and burls to handle. It looks ugly here.
                nurl?.let { url ->
                    val completeUrl = url.replace("\${AUCTION_PRICE}", price.toString())
//                    CloudXLogger.debug("BannerImpl", "NURL: Sending for $adUnitId")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val response: HttpResponse = HttpClient().get(completeUrl)
                            if (response.status.isSuccess()) {
                                val statusCode = response.status
//                                CloudXLogger.debug("BannerImpl", "NURL: Success for $adUnitId, ${statusCode.value}")
                            } else {
//                                CloudXLogger.error("BannerImpl", "Failed to call nurl status: ${response.status}")
                            }
                        } catch (e: Exception) {
//                            CloudXLogger.error("BannerImpl", "Error calling nurl error: ${e.message}")
                        }
                    }
                }
            }
        }

        override fun onClick() {
            scope.launch { _event.emit(SuspendableBannerEvent.Click) }
        }

        override fun onError(error: io.cloudx.sdk.internal.adapter.CloudXAdError) {
            scope.launch {
                _event.emit(SuspendableBannerEvent.Error(error))
                // 1 liner instead of _event.collect { /*assign error*/ }
                _lastErrorEvent.value = error
            }
        }
    })

    override suspend fun load(): Boolean {
        val evtJob = scope.async {
            event.first {
                it is SuspendableBannerEvent.Load || it is SuspendableBannerEvent.Error
            }
        }

        banner.load()

        return evtJob.await() is SuspendableBannerEvent.Load
    }

    override fun timeout() {
        // unused
    }

    private val _event = MutableSharedFlow<SuspendableBannerEvent>()
    override val event: SharedFlow<SuspendableBannerEvent> = _event

    private val _lastErrorEvent = MutableStateFlow<io.cloudx.sdk.internal.adapter.CloudXAdError?>(null)
    override val lastErrorEvent: StateFlow<io.cloudx.sdk.internal.adapter.CloudXAdError?> = _lastErrorEvent

    override fun destroy() {
        scope.cancel()
        banner.destroy()
    }
}