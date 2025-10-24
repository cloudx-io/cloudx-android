package io.cloudx.sdk.internal.db.imp_tracking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedTrackingEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CachedTrackingEvents)

    @Query("SELECT * FROM cached_tracking_events_table")
    suspend fun getAll(): List<CachedTrackingEvents>

    @Query("DELETE FROM cached_tracking_events_table WHERE id = :id")
    suspend fun delete(id: String)
}
