package io.cloudx.sdk.internal.db.imp_tracking

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_tracking_events_table")
data class CachedTrackingEvents(
    @PrimaryKey val id: String, // e.g. auctionId or UUID

    val encoded: String,
    val campaignId: String,
    val eventValue: String,
    val eventName: String,
    val type: String

)
