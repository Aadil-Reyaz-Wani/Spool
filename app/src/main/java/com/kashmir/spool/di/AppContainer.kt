package com.kashmir.spool.di

import com.kashmir.spool.data.repository.SpoolRepository

interface AppContainer {
    val spoolRepository: SpoolRepository
}