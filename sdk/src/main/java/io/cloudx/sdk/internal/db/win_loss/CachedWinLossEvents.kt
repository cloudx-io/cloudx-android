package io.cloudx.sdk.internal.db.win_loss

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_win_loss_events_table")
data class CachedWinLossEvents(
    @PrimaryKey val id: String,
    val auctionId: String,
    val bidId: String,
    val state: String,
    val payload: String?,
    val lossPayload: String?,
    val sent: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val STATE_NEW = "NEW"
        const val STATE_LOADED = "LOADED"
        const val STATE_WIN = "WIN"
        const val STATE_LOSS = "LOSS"
    }
}
