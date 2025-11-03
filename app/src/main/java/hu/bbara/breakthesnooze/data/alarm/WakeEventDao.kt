package hu.bbara.breakthesnooze.data.alarm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WakeEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: WakeEventEntity): Long

    @Query("SELECT * FROM wake_events ORDER BY completed_at DESC")
    fun observeEvents(): Flow<List<WakeEventEntity>>

    @Query("DELETE FROM wake_events WHERE completed_at < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)
}
