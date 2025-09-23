package io.cloudx.sdk.internal.db.win_loss

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_win_loss_events_table")
data class CachedWinLossEvents(
    @PrimaryKey val id: String,
    val payload: String
    ,
    val auctionId: String,
    val bidId: String,
    val state: String, // "LOADED", "WIN", "LOSS"
    val bidJson: String,
    val timestamp: Long
) {
    companion object {
        const val STATE_LOADED = "LOADED"
        const val STATE_WIN = "WIN"
        const val STATE_LOSS = "LOSS"
    }
}