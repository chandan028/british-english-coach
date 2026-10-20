package com.britishcoach.app

import android.app.Application
import android.content.ComponentCallbacks2

class CoachApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Free the model's memory if Android is about to kill the app; it reloads on demand.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) container.model.unload()
    }
}
