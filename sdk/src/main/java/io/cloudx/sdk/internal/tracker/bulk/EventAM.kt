package io.cloudx.sdk.internal.tracker.bulk

data class EventAM(
    val impression: String,     // impression
    val campaignId: String,  // campaignId
    val eventValue: String,     // "N/A"
    val eventName: String,   // eventName
    val type: String,   // pathSegment
)

