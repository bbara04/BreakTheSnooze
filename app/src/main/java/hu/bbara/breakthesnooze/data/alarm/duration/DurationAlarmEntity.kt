package hu.bbara.breakthesnooze.data.alarm.duration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "duration_alarms",
    indices = [Index(value = ["trigger_at"])]
)
data class DurationAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "trigger_at") val triggerAtMillis: Long,
    @ColumnInfo(name = "sound_uri") val soundUri: String?,
    @ColumnInfo(name = "dismiss_task") val dismissTask: String,
    @ColumnInfo(name = "qr_barcode_value") val qrBarcodeValue: String?,
    @ColumnInfo(name = "qr_unique_required_count", defaultValue = "0") val qrUniqueRequiredCount: Int
)
