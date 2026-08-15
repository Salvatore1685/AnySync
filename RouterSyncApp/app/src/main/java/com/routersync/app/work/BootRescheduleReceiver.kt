package com.routersync.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // WorkManager persiste già i PeriodicWorkRequest tra i riavvii; questo passaggio
            // serve soprattutto a ripristinare correttamente lo schedule MONTHLY (one-time)
            // per i profili che lo usano.
            SyncScheduler(context.applicationContext).rescheduleAll()
        }
    }
}
