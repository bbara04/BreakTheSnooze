package hu.bbara.breakthesnooze.data.alarm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, WakeEventEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun wakeEventDao(): WakeEventDao

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
                    MIGRATION_5_6
                ).build().also { INSTANCE = it }
            }
        }
    }
}
