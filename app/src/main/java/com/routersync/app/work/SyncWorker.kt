package com.routersync.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.routersync.app.R
import com.routersync.app.data.AppDatabase
import com.routersync.app.data.ScheduleType
import com.routersync.app.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Esegue la sincronizzazione di un singolo [SyncProfile]. Viene lanciato sia dalle
 * pianificazioni ricorrenti (WorkManager PeriodicWorkRequest) sia dal tasto di sync manuale.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID = 42
        private const val LOW_SPACE_CHANNEL_ID = "low_space_channel"
        private const val LOW_SPACE_NOTIFICATION_ID_BASE = 10_000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profileId = inputData.getLong(KEY_PROFILE_ID, -1L)
        if (profileId == -1L) return@withContext Result.failure()

        val dao = AppDatabase.getInstance(applicationContext).syncProfileDao()
        val profile = dao.getById(profileId) ?: return@withContext Result.failure()

        // Condizioni di rete che WorkManager non può verificare da solo (es. Wi-Fi di casa
        // specifico): se non soddisfatte, riprova più tardi senza considerarlo un errore vero —
        // ma lo segnaliamo comunque, così l'utente vede perché è in attesa invece di uno
        // spinner muto.
        if (!NetworkConditionChecker.isSatisfied(applicationContext, profile)) {
            val reason = NetworkConditionChecker.reasonNotSatisfied(applicationContext, profile)
            dao.updateSyncResult(
                id = profile.id,
                timestamp = System.currentTimeMillis(),
                status = "In attesa: $reason"
            )
            return@withContext Result.retry()
        }

        setForeground(createForegroundInfo("Avvio sincronizzazione…"))

        val engine = SyncEngine(applicationContext)
        val result = engine.run(
            profile = profile,
            onProgress = { current, done, total ->
                // Aggiorna la notifica con il progresso (best-effort, non blocca in caso di errore)
                runCatching {
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification("Sincronizzo: $current", done, total))
                }
            },
            isCancelled = { isStopped }
        )

        dao.updateSyncResult(
            id = profile.id,
            timestamp = System.currentTimeMillis(),
            status = when {
                result.cancelled -> "Interrotta dall'utente - ${result.filesTransferred} file trasferiti"
                result.success -> "OK - ${result.filesTransferred} file trasferiti"
                else -> result.message
            }
        )
        result.updatedManifest?.let { manifest ->
            dao.updateManifest(profile.id, manifest)
        }

        checkLowSpaceAndNotify(profile)

        // Per Giornaliera/Settimanale/Mensile, ri-pianifichiamo qui il prossimo avvio preciso
        // (WorkManager non supporta nativamente intervalli allineati a un giorno/ora specifici).
        if (profile.scheduleType == ScheduleType.DAILY ||
            profile.scheduleType == ScheduleType.WEEKLY ||
            profile.scheduleType == ScheduleType.MONTHLY
        ) {
            SyncScheduler(applicationContext).schedulePrecise(profile)
        }

        when {
            result.cancelled -> Result.success() // interrotto volontariamente: non va ritentato in automatico
            result.success -> Result.success()
            else -> Result.retry()
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        createChannelIfNeeded()
        val notification = buildNotification(message, 0, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String, done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("EverySync")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .apply { if (total > 0) setProgress(total, done, false) }
            .build()

    /** Controlla lo spazio libero sull'HDD di questa sync e, se sotto soglia, invia una notifica dedicata. */
    private fun checkLowSpaceAndNotify(profile: com.routersync.app.data.SyncProfile) {
        if (profile.protocol != com.routersync.app.data.RemoteProtocol.SMB) return
        val settings = com.routersync.app.data.AppSettings(applicationContext)
        if (!settings.shouldNotifyLowSpace()) return

        val client = com.routersync.app.remote.RemoteClientFactory.create(profile)
        val freeGb = runCatching {
            client.connect()
            client.freeSpaceBytes()
        }.getOrNull()?.let { it / 1_073_741_824.0 }
        runCatching { client.disconnect() }

        if (freeGb != null && freeGb < profile.storageWarningThresholdGb) {
            createLowSpaceChannelIfNeeded()
            val notification = NotificationCompat.Builder(applicationContext, LOW_SPACE_CHANNEL_ID)
                .setContentTitle("Spazio HDD in esaurimento")
                .setContentText("\"${profile.name}\": solo %.1f GB liberi rimasti (soglia: ${profile.storageWarningThresholdGb} GB)".format(freeGb))
                .setSmallIcon(R.drawable.ic_sync)
                .setAutoCancel(true)
                .build()
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(LOW_SPACE_NOTIFICATION_ID_BASE + profile.id.toInt(), notification)
        }
    }

    private fun createLowSpaceChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(LOW_SPACE_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(LOW_SPACE_CHANNEL_ID, "Spazio HDD in esaurimento", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Sincronizzazione", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}
