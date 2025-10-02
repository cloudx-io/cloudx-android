package io.cloudx.sdk.internal.db.win_loss

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface CachedWinLossEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: CachedWinLossEvents)

    @Query("SELECT * FROM cached_win_loss_events_table WHERE state = :state")
    suspend fun getAllByState(state: String): List<CachedWinLossEvents>

    @Query("SELECT * FROM cached_win_loss_events_table WHERE auctionId = :auctionId AND bidId = :bidId LIMIT 1")
    suspend fun findByAuctionAndBid(auctionId: String, bidId: String): CachedWinLossEvents?

    @Query("DELETE FROM cached_win_loss_events_table WHERE auctionId = :auctionId AND bidId = :bidId")
    suspend fun deleteByAuctionAndBid(auctionId: String, bidId: String)

    @Query("DELETE FROM cached_win_loss_events_table WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM cached_win_loss_events_table WHERE sent = 0")
    suspend fun getAllUnsent(): List<CachedWinLossEvents>

    @Query("SELECT * FROM cached_win_loss_events_table WHERE sent = 1 AND state IN (:states)")
    suspend fun getAllSentByStates(states: List<String>): List<CachedWinLossEvents>
}
