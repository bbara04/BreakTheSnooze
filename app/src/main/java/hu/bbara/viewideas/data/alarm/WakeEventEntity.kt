package hu.bbara.viewideas.data.alarm

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wake_events",
    indices = [Index("completed_at")]
)
data class WakeEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "alarm_id")
    val alarmId: Int,
    @ColumnInfo(name = "alarm_label")
    val alarmLabel: String,
    @ColumnInfo(name = "dismiss_task")
    val dismissTask: String,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long
)
