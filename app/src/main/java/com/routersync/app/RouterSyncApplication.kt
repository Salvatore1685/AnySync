package com.routersync.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.routersync.app.work.MaintenanceWorker
import com.routersync.app.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RouterSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Il database si inizializza lazy al primo utilizzo. Qui registriamo solo il controllo
        // periodico (avviso "sync ferma" + backup automatico): KEEP lo lascia com'è se già pianificato.
        runCatching {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                MaintenanceWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
        // Autoriparazione: a ogni avvio dell'app verifica che ogni sync pianificata abbia
        // davvero un prossimo avvio in programma, e se manca lo ricrea.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { SyncScheduler(this@RouterSyncApplication).ensureScheduled() }
        }
    }
}
