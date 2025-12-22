package com.aadil.spool.di

import com.aadil.spool.data.repository.SpoolRepository

interface AppContainer {
    val spoolRepository: SpoolRepository
}