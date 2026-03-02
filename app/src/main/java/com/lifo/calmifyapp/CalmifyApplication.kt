package com.lifo.calmifyapp

import android.app.Application
import com.lifo.calmifyapp.di.allKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CalmifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@CalmifyApplication)
            modules(allKoinModules)
        }
    }
}
