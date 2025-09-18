package io.cloudx.sdk.internal.db.win_loss

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface CachedWinLossEventDao {

    @Query("SELECT * FROM cached_win_loss_events_table")
    suspend fun getAll(): List<CachedWinLossEvents>

    @Insert
    suspend fun insert(event: CachedWinLossEvents)

    @Query("DELETE FROM cached_win_loss_events_table WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_win_loss_events_table")
    suspend fun deleteAll()
}