package hu.bbara.breakthesnooze.data.alarm.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "time") val time: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "repeat_days") val repeatDays: String,
    @ColumnInfo(name = "sound_uri") val soundUri: String?,
    @ColumnInfo(name = "dismiss_task") val dismissTask: String,
    @ColumnInfo(name = "qr_barcode_value") val qrBarcodeValue: String?,
    @ColumnInfo(name = "qr_unique_required_count") val qrUniqueRequiredCount: Int
)
