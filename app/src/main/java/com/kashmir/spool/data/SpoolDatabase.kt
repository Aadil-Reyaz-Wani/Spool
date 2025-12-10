package com.kashmir.spool.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.kashmir.spool.data.dao.SpoolDao
import com.kashmir.spool.data.entity.Filament
import kotlin.concurrent.Volatile

//@AutoMigration(from = 3, to = 4, spec = SpoolDatabase::class)
@Database(
    entities = [Filament::class],
    version = 8,
    autoMigrations = [
        AutoMigration( from = 4, to = 5, spec = SpoolDatabase.MyAutoMigration::class ),
        AutoMigration( from = 5, to = 6 ),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8)
    ],
    exportSchema = true
)
abstract class SpoolDatabase : RoomDatabase() {

    @DeleteColumn(tableName = "filaments", columnName = "temp_bed")
    class MyAutoMigration : AutoMigrationSpec

    abstract fun spoolDao(): SpoolDao


    companion object {
        @Volatile
        var Instance: SpoolDatabase? = null

        fun getDatabase(context: Context): SpoolDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context = context,
                    klass = SpoolDatabase::class.java,
                    name = "spool_database"
                )
                    .build()
                    .also { Instance = it }
            }
        }
    }
}