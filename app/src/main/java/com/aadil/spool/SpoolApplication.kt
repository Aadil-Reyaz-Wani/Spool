package com.aadil.spool

import android.app.Application
import com.aadil.spool.di.AppContainer
import com.aadil.spool.di.DefaultAppContainer

class SpoolApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }
}