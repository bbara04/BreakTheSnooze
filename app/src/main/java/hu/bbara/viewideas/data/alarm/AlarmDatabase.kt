package hu.bbara.viewideas.data.alarm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

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

        fun getInstance(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarms.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
            }
        }
    }
}
