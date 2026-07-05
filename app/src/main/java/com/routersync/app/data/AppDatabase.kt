package com.routersync.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromProtocol(p: RemoteProtocol): String = p.name
    @TypeConverter
    fun toProtocol(s: String): RemoteProtocol = RemoteProtocol.valueOf(s)

    @TypeConverter
    fun fromSchedule(s: ScheduleType): String = s.name
    @TypeConverter
    fun toSchedule(s: String): ScheduleType = ScheduleType.valueOf(s)

    @TypeConverter
    fun fromDirection(d: SyncDirection): String = d.name
    @TypeConverter
    fun toDirection(s: String): SyncDirection = SyncDirection.valueOf(s)
}

/**
 * Migrazione dalla versione 1 alla 2: aggiunge i campi per la liberazione automatica dello
 * spazio, la propagazione delle cancellazioni bidirezionali, e il registro dell'ultimo stato
 * sincronizzato. Preserva tutti i profili di sincronizzazione già salvati dall'utente.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_profiles ADD COLUMN autoFreeSpaceAfterSync INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_profiles ADD COLUMN mirrorDeletes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_profiles ADD COLUMN lastSyncManifest TEXT")
    }
}

@Database(entities = [SyncProfile::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncProfileDao(): SyncProfileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "routersync.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
