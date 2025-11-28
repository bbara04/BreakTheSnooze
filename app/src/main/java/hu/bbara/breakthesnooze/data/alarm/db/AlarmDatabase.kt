package hu.bbara.breakthesnooze.data.alarm.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import hu.bbara.breakthesnooze.data.duration.db.DurationAlarmDao
import hu.bbara.breakthesnooze.data.duration.db.DurationAlarmEntity

@Database(
    entities = [AlarmEntity::class, WakeEventEntity::class, DurationAlarmEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun wakeEventDao(): WakeEventDao
    abstract fun durationAlarmDao(): DurationAlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AlarmDatabase? = null

        private val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { database ->
            database.execSQL("ALTER TABLE alarms ADD COLUMN sound_uri TEXT")
        }

        private val MIGRATION_2_3 = androidx.room.migration.Migration(2, 3) { database ->
            database.execSQL(
                "ALTER TABLE alarms ADD COLUMN dismiss_task TEXT NOT NULL DEFAULT 'math_challenge'"
            )
        }

        private val MIGRATION_3_4 = androidx.room.migration.Migration(3, 4) { database ->
            database.execSQL("ALTER TABLE alarms ADD COLUMN qr_barcode_value TEXT")
        }

        private val MIGRATION_4_5 = androidx.room.migration.Migration(4, 5) { database ->
            database.execSQL(
                "ALTER TABLE alarms ADD COLUMN qr_unique_required_count INTEGER NOT NULL DEFAULT 0"
            )
        }

        private val MIGRATION_5_6 = androidx.room.migration.Migration(5, 6) { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS wake_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "alarm_id INTEGER NOT NULL, " +
                    "alarm_label TEXT NOT NULL, " +
                    "dismiss_task TEXT NOT NULL, " +
                    "completed_at INTEGER NOT NULL"
                    + ")"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_wake_events_completed_at ON wake_events(completed_at)"
            )
        }

        private val MIGRATION_6_7 = androidx.room.migration.Migration(6, 7) { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS duration_alarms (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "label TEXT NOT NULL, " +
                    "duration_minutes INTEGER NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "trigger_at INTEGER NOT NULL, " +
                    "sound_uri TEXT, " +
                    "dismiss_task TEXT NOT NULL, " +
                    "qr_barcode_value TEXT, " +
                    "qr_unique_required_count INTEGER NOT NULL DEFAULT 0" +
                    ")"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_duration_alarms_trigger_at ON duration_alarms(trigger_at)"
            )
        }

        fun getInstance(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarms.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                ).build().also { INSTANCE = it }
            }
        }
    }
}
