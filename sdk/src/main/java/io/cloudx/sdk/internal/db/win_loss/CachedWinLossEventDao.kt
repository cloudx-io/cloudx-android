package io.cloudx.sdk.internal.db.win_loss

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface CachedWinLossEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: CachedWinLossEvents): Long

    @Query("DELETE FROM cached_win_loss_events_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM cached_win_loss_events_table")
    suspend fun getAllUnsent(): List<CachedWinLossEvents>
}
