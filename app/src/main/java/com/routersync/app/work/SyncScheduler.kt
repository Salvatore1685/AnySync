package com.routersync.app.work

import android.content.Context
import androidx.work.*
import com.routersync.app.data.AppDatabase
import com.routersync.app.data.NetworkPreference
import com.routersync.app.data.ScheduleType
import com.routersync.app.data.SyncProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Centralizza la creazione/cancellazione dei lavori WorkManager per ciascun profilo.
 *
 * Per Giornaliera/Settimanale/Mensile viene sempre calcolato l'esatto prossimo orario
 * richiesto (giorno + ora + minuto) e pianificato come lavoro singolo che si ri-pianifica
 * da solo al termine di ogni esecuzione — più prevedibile del semplice "ripeti ogni N ore"
 * di WorkManager, e permette di rispettare con precisione la scelta dell'utente.
 *
 * Le condizioni di avvio (rete/carica) vengono tradotte in vincoli nativi di WorkManager
 * dove possibile; il controllo più fine (Wi-Fi di casa specifico) avviene invece dentro
 * [SyncWorker] stesso al momento dell'esecuzione, con nuovo tentativo automatico se non
 * ancora soddisfatto.
 */
class SyncScheduler(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private fun uniqueWorkName(profileId: Long) = "sync_profile_$profileId"

    private fun buildConstraints(profile: SyncProfile): Constraints {
        // Usiamo sempre CONNECTED come vincolo nativo di WorkManager: il controllo più preciso
        // (Wi-Fi specifico, Wi-Fi di casa, dati mobili) avviene dentro il Worker stesso tramite
        // NetworkConditionChecker. Il vincolo nativo UNMETERED, usato in precedenza per "Wi-Fi",
        // si è rivelato inaffidabile: alcune reti Wi-Fi (anche quella di casa) vengono segnalate
        // da Android come "a consumo" per motivi tecnici indipendenti dall'essere Wi-Fi, facendo
        // restare la sincronizzazione bloccata in attesa di una condizione mai soddisfatta.
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(profile.requiresCharging)
            .build()
    }

    /** Applica (o riapplica) la pianificazione per un profilo, sostituendo quella precedente. */
    fun schedule(profile: SyncProfile) {
        cancel(profile.id)
        when (profile.scheduleType) {
            ScheduleType.MANUAL -> { /* nessuna pianificazione: si avvia solo con runManualSync() */ }
            ScheduleType.HOURLY -> scheduleHourly(profile)
            ScheduleType.DAILY, ScheduleType.WEEKLY, ScheduleType.MONTHLY -> schedulePrecise(profile)
        }
    }

    private fun scheduleHourly(profile: SyncProfile) {
        // La prima esecuzione parte esattamente all'ora:minuto scelti (oggi se ancora futuro,
        // altrimenti domani); da quel momento in poi si ripete ogni ora, sempre allo stesso minuto.
        val now = Calendar.getInstance()
        val firstRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, profile.scheduledHour)
            set(Calendar.MINUTE, profile.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delayMs = (firstRun.timeInMillis - now.timeInMillis).coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(buildConstraints(profile))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profile.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName(profile.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        persistWorkId(profile.id, request.id.toString())
    }

    /** Calcola la prossima data/ora esatta in cui la sync deve partire, in base al piano scelto. */
    private fun computeNextOccurrence(profile: SyncProfile): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, profile.scheduledHour)
            set(Calendar.MINUTE, profile.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (profile.scheduleType) {
            ScheduleType.DAILY -> {
                if (!next.after(now)) next.add(Calendar.DAY_OF_MONTH, 1)
            }
            ScheduleType.WEEKLY -> {
                next.set(Calendar.DAY_OF_WEEK, profile.scheduledDayOfWeek)
                if (!next.after(now)) next.add(Calendar.WEEK_OF_YEAR, 1)
            }
            ScheduleType.MONTHLY -> {
                val maxDay = next.getActualMaximum(Calendar.DAY_OF_MONTH)
                next.set(Calendar.DAY_OF_MONTH, minOf(profile.scheduledDayOfMonth, maxDay))
                if (!next.after(now)) {
                    next.add(Calendar.MONTH, 1)
                    val newMax = next.getActualMaximum(Calendar.DAY_OF_MONTH)
                    next.set(Calendar.DAY_OF_MONTH, minOf(profile.scheduledDayOfMonth, newMax))
                }
            }
            else -> {}
        }
        return next.timeInMillis
    }

    /** Pianifica (o ri-pianifica) il prossimo avvio preciso per Giornaliera/Settimanale/Mensile. */
    fun schedulePrecise(profile: SyncProfile) {
        val delayMs = (computeNextOccurrence(profile) - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(buildConstraints(profile))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_PROFILE_ID to profile.id))
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(profile.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
        persistWorkId(profile.id, request.id.toString())
    }

    /** Avvia una sincronizzazione immediata (tasto manuale), rispettando le condizioni di rete/carica scelte per il profilo. */
    fun runManualSync(profile: SyncProfile) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(buildConstraints(profile))
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

    /**
     * Interrompe la sincronizzazione attualmente in corso, sia essa manuale o pianificata.
     * Dopo l'interruzione, ripristina subito la normale pianificazione futura del profilo
     * (per una sync oraria/giornaliera/ecc. questo significa: salta questo giro, riparte al
     * prossimo orario previsto — non elimina la ricorrenza).
     */
    fun stopRunningSync(profile: SyncProfile) {
        workManager.cancelUniqueWork(uniqueWorkName(profile.id))
        workManager.cancelUniqueWork("${uniqueWorkName(profile.id)}_manual")
        schedule(profile)
    }

    private fun persistWorkId(profileId: Long, workId: String) {
        val dao = AppDatabase.getInstance(context).syncProfileDao()
        runBlocking { dao.updateWorkRequestId(profileId, workId) }
    }

    /** Ripristina tutte le pianificazioni salvate: usato dopo un riavvio del dispositivo. */
    fun rescheduleAll() {
        val dao = AppDatabase.getInstance(context).syncProfileDao()
        runBlocking {
            val profiles = dao.observeAll().first()
            profiles.forEach { schedule(it) }
        }
    }
}
