package com.lifo.calmifyapp

import android.app.Application
import com.lifo.calmifyapp.di.allKoinModules
import com.lifo.mongo.sync.SyncEngine
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CalmifyApplication : Application() {

    private val syncEngine: SyncEngine by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@CalmifyApplication)
            modules(allKoinModules)
        }

        // Start offline-first sync engine — drains queue on connectivity, periodic delta pull
        syncEngine.start()
    }
}
