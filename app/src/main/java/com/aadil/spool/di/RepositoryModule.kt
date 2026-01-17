package com.aadil.spool.di

import com.aadil.spool.data.repository.OfflineSpoolRepository
import com.aadil.spool.data.repository.SpoolRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSpoolRepository(
        spoolRepositoryImpl: OfflineSpoolRepository
    ) : SpoolRepository

}