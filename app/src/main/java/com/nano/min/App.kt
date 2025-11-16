package com.nano.min

import android.app.Application
import com.nano.min.di.dataModule
import com.nano.min.di.interactorModule
import com.nano.min.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(dataModule, interactorModule, viewModelModule)
        }
    }
}
