package com.kashmir.spool

import android.app.Application
import com.kashmir.spool.di.AppContainer
import com.kashmir.spool.di.DefaultAppContainer

class SpoolApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }
}