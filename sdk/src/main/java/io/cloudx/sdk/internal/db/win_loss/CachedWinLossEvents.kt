package io.cloudx.sdk.internal.db.win_loss

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_win_loss_events_table")
data class CachedWinLossEvents(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val auctionId: String,
    val bidId: String,
    val payload: String,
    val createdAt: Long
)
