package io.cloudx.sdk.internal.tracker.metrics

import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.tracker.bulk.EventTrackerBulkApi
import io.cloudx.sdk.internal.util.ThreadUtils

internal interface MetricsTracker {

    fun start(config: Config)

    fun setBasicData(sessionId: String, accountId: String, basePayload: String)

    fun trackMethodCall(type: MetricsType.Method)

    fun trackNetworkCall(type: MetricsType.Network, latency: Long)

    fun trySendingPendingMetrics()

    fun stop()

}

internal fun MetricsTracker(): MetricsTracker = LazySingleInstance

private val LazySingleInstance by lazy {
    MetricsTrackerImpl(
        ThreadUtils.GlobalIOScope,
        EventTrackerBulkApi(),
        Database()
    )
}