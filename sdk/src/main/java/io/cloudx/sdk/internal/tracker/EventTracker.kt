package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.util.ThreadUtils

internal interface EventTracker {

    fun trySendingPendingTrackingEvents()

    fun setEndpoint(endpointUrl: String?)

    fun send(
        encoded: String,
        campaignId: String,
        eventValue: String,
        eventType: EventType
    )
}

internal fun EventTracker(): EventTracker = LazySingleInstance

private val LazySingleInstance by lazy {
    EventTrackerImpl(
        ThreadUtils.GlobalIOScope,
        Database()
    )
}