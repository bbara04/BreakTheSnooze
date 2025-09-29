package hu.bbara.viewideas.data.alarm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class],
    version = 3,
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

        fun getInstance(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarms.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
