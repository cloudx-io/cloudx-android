package io.cloudx.sdk.internal.db.metrics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MetricsEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: MetricsEvent)

    @Query("SELECT * FROM metrics_event_table WHERE metricName = :metricName LIMIT 1")
    suspend fun getAllByMetric(metricName: String): MetricsEvent?

    @Query("DELETE FROM metrics_event_table WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM metrics_event_table")
    suspend fun getAll(): List<MetricsEvent>

}
