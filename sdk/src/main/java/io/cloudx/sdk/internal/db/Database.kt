package io.cloudx.sdk.internal.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.db.imp_tracking.CachedTrackingEventDao
import io.cloudx.sdk.internal.db.imp_tracking.CachedTrackingEvents
import io.cloudx.sdk.internal.db.metrics.MetricsEvent
import io.cloudx.sdk.internal.db.metrics.MetricsEventDao
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEventDao
import io.cloudx.sdk.internal.db.win_loss.CachedWinLossEvents

@Database(
    entities = [
        CachedTrackingEvents::class,
        MetricsEvent::class,
        CachedWinLossEvents::class
    ],
    version = 11,
    // Not yet.
    exportSchema = false
)
internal abstract class CloudXDb : RoomDatabase() {

    // region EventTracking
    abstract fun cachedTrackingEventDao(): CachedTrackingEventDao
    // endregion

    // region WinLoss
    abstract fun cachedWinLossEventDao(): CachedWinLossEventDao
    // endregion

    // region Metrics
    abstract fun metricsEventDao(): MetricsEventDao
    // endregion
}

internal fun Database(): CloudXDb = LazySingleInstance

private val LazySingleInstance by lazy {
    Room.databaseBuilder(
        ApplicationContext(),
        CloudXDb::class.java,
        "cloudx"
    )
        .fallbackToDestructiveMigration()
        .build()
}