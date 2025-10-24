package io.cloudx.sdk.internal.db.metrics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metrics_event_table")
data class MetricsEvent(
    @PrimaryKey val id: String, // e.g. auctionId or UUID
    val metricName: String,
    var counter: Long = 0,
    var totalLatency: Long = 0L, // in ms or as needed
    val sessionId: String,
    val auctionId: String, // generate a unique one per event
)