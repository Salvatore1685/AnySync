package com.routersync.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.routersync.app.R
import com.routersync.app.data.AppDatabase
import com.routersync.app.data.AppSettings
import com.routersync.app.data.AutoBackup
import com.routersync.app.data.ScheduleType
import com.routersync.app.data.SyncProfile
import java.util.concurrent.TimeUnit

/**
 * Controllo periodico (ogni 12 ore) indipendente dalle sync:
 *  1) avvisa se una sync pianificata non riesce da più del previsto (es. bloccata dal risparmio
 *     batteria, rete cambiata, password cambiata), così non si scopre dopo giorni;
 *  2) esegue il backup automatico settimanale della configurazione, se attivo.
 */
class MaintenanceWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_NAME = "anysync_maintenance"
        private const val ALERT_CHANNEL_ID = "alert_channel"
        private const val ALERT_NOTIFICATION_ID_BASE = 20_000
        private val BACKUP_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
        private val RENOTIFY_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
    }

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)

        // Prima di tutto: se qualche sync pianificata ha perso il suo prossimo avvio, lo ricrea.
        runCatching { SyncScheduler(applicationContext).ensureScheduled() }
        runCatching { if (settings.staleSyncNotificationsEnabled) checkStaleSyncs(settings) }
        runCatching {
            if (settings.autoBackupEnabled &&
                System.currentTimeMillis() - settings.lastAutoBackupTimestamp >= BACKUP_INTERVAL_MS
            ) {
                AutoBackup.run(applicationContext)
            }
        }
        return Result.success()
    }

    /** Dopo quanto tempo senza una sync riuscita conviene avvisare, in base alla frequenza scelta. */
    private fun staleThresholdMs(profile: SyncProfile): Long? = when (profile.scheduleType) {
        ScheduleType.MANUAL -> null
        ScheduleType.HOURLY -> TimeUnit.HOURS.toMillis(12)
        ScheduleType.DAILY -> TimeUnit.HOURS.toMillis(36)
        ScheduleType.WEEKLY -> TimeUnit.DAYS.toMillis(9)
        ScheduleType.MONTHLY -> TimeUnit.DAYS.toMillis(35)
    }

    private suspend fun checkStaleSyncs(settings: AppSettings) {
        val db = AppDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()
        db.syncProfileDao().getAllOnce().forEach { profile ->
            val threshold = staleThresholdMs(profile) ?: return@forEach
            val lastSuccess = db.syncLogDao().lastSuccessTimestamp(profile.id) ?: return@forEach
            val silentFor = now - lastSuccess
            if (silentFor < threshold) return@forEach
            if (now - settings.lastStaleNotification(profile.id) < RENOTIFY_INTERVAL_MS) return@forEach

            notifyStale(profile, silentFor)
            settings.setLastStaleNotification(profile.id, now)
        }
    }

    private fun notifyStale(profile: SyncProfile, silentForMs: Long) {
        createChannelIfNeeded()
        val days = TimeUnit.MILLISECONDS.toDays(silentForMs)
        val since = if (days >= 1) "da $days ${if (days == 1L) "giorno" else "giorni"}"
        else "da ${TimeUnit.MILLISECONDS.toHours(silentForMs)} ore"
        val lastStatus = profile.lastSyncStatus?.takeIf { it.isNotBlank() }
        val text = buildString {
            append("\"${profile.name}\": nessuna sincronizzazione riuscita $since.")
            if (lastStatus != null) append(" Ultimo stato: $lastStatus")
        }
        val notification = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL_ID)
            .setContentTitle("Sync ferma")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_sync)
            .setAutoCancel(true)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALERT_NOTIFICATION_ID_BASE + profile.id.toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL_ID, "Sync ferma", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }
}
