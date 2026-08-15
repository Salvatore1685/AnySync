package com.routersync.app

import android.app.Application

class RouterSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Il database e WorkManager si inizializzano lazy al primo utilizzo (vedi AppDatabase.getInstance
        // e WorkManager.getInstance), quindi qui non serve fare nulla di esplicito.
    }
}
