package com.routersync.app.work

import android.content.Context
import androidx.work.*
import com.routersync.app.data.AppDatabase
import com.routersync.app.data.ScheduleType
import com.routersync.app.data.SyncProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Centralizza la creazione/cancellazione dei lavori WorkManager per ciascun profilo,
 * traducendo il piano scelto dall'utente (orario/giornaliero/settimanale/mensile/manuale)
 * nelle API di WorkManager più adatte.
 */
class SyncScheduler(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private fun uniqueWorkName(profileId: Long) = "sync_profile_$profileId"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Applica (o riapplica) la pianificazione per un profilo, sostituendo quella precedente. */
    fun schedule(profile: SyncProfile) {
        cancel(profile.id)
        when (profile.scheduleType) {
            ScheduleType.MANUAL -> { /* nessuna pianificazione: si avvia solo con runManualSync() */ }
            ScheduleType.HOURLY -> scheduleRecurring(profile, 1, TimeUnit.HOURS)
            ScheduleType.DAILY -> scheduleRecurring(profile, 1, TimeUnit.DAYS)
            ScheduleType.WEEKLY -> scheduleRecurring(profile, 7, TimeUnit.DAYS)
            ScheduleType.MONTHLY -> scheduleMonthly(profile)
        }
    }

    private fun scheduleRecurring(profile: SyncProfile, interval: Long, unit: TimeUnit) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, unit)
            .setConstraints(networkConstraints)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profile.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName(profile.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        persistWorkId(profile.id, request.id.toString())
    }

    /**
     * WorkManager non ha un intervallo "mensile" nativo (i mesi hanno lunghezza variabile),
     * quindi pianifichiamo un singolo OneTimeWorkRequest per la prossima esecuzione;
     * il Worker stesso, a fine lavoro, richiama questo metodo per pianificare il mese successivo.
     */
    fun scheduleMonthly(profile: SyncProfile) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }
        val delayMs = next.timeInMillis - now.timeInMillis

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profile.id))
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(profile.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
        persistWorkId(profile.id, request.id.toString())
    }

    /** Avvia una sincronizzazione immediata (tasto manuale), indipendentemente dallo schedule. */
    fun runManualSync(profile: SyncProfile) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profile.id))
            .build()
        workManager.enqueueUniqueWork(
            "${uniqueWorkName(profile.id)}_manual",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(profileId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(profileId))
    }

    /** Interrompe la sincronizzazione manuale attualmente in corso (se presente), senza toccare le pianificazioni ricorrenti. */
    fun cancelManualSync(profileId: Long) {
        workManager.cancelUniqueWork("${uniqueWorkName(profileId)}_manual")
    }

    private fun persistWorkId(profileId: Long, workId: String) {
        val dao = AppDatabase.getInstance(context).syncProfileDao()
        runBlocking { dao.updateWorkRequestId(profileId, workId) }
    }

    /** Ripristina tutte le pianificazioni salvate: usato dopo un riavvio del dispositivo. */
    fun rescheduleAll() {
        val dao = AppDatabase.getInstance(context).syncProfileDao()
        runBlocking {
            // observeAll è un Flow: qui prendiamo solo il primo valore emesso
            val profiles = dao.observeAll().first()
            profiles.forEach { schedule(it) }
        }
    }
}
