package hu.bbara.breakthesnooze.data.duration.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DurationAlarmDao {
    @Query("SELECT * FROM duration_alarms ORDER BY trigger_at ASC")
    fun observeDurationAlarms(): Flow<List<DurationAlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DurationAlarmEntity): Long

    @Query("DELETE FROM duration_alarms WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM duration_alarms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): DurationAlarmEntity?
}
