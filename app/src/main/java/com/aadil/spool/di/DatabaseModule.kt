package com.aadil.spool.di

import android.content.Context
import androidx.room.Room
import com.aadil.spool.data.SpoolDatabase
import com.aadil.spool.data.dao.SpoolDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // 1. Teach hilt how to create DATABASE
    @Provides
    @Singleton // This tells the hilt to create the database once
    fun provideDatabase(@ApplicationContext context: Context) : SpoolDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = SpoolDatabase::class.java,
            name = "spool_database"
        )
            .build()
    }

    // 2. Teach hilt how to create DAO
    @Provides
    fun provideSpoolDao(spoolDatabase: SpoolDatabase) : SpoolDao{
        return spoolDatabase.spoolDao()
    }
}