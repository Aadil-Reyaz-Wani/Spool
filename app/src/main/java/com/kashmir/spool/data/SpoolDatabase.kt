package com.kashmir.spool.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kashmir.spool.data.dao.SpoolDao
import com.kashmir.spool.data.entity.Filament
import kotlin.concurrent.Volatile


@Database(entities = [Filament::class], version = 2, exportSchema = false)
abstract class SpoolDatabase : RoomDatabase(){

    abstract fun spoolDao(): SpoolDao


    companion object {
        @Volatile
        var Instance: SpoolDatabase ? = null

        fun getDatabase(context: Context) : SpoolDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context = context,
                    klass = SpoolDatabase::class.java,
                    name = "spool_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}