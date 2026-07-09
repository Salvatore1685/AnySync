package com.routersync.app.data

import android.content.Context
import com.routersync.app.sync.FreeSpaceResult
import com.routersync.app.sync.SyncEngine
import com.routersync.app.work.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SyncProfileRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).syncProfileDao()
    private val scheduler = SyncScheduler(context)

    fun observeProfiles(): Flow<List<SyncProfile>> = dao.observeAll()

    /** Salva il profilo e applica/aggiorna la sua pianificazione WorkManager. */
    suspend fun saveProfile(profile: SyncProfile): Long {
        val id = dao.upsert(profile)
        val saved = if (profile.id == 0L) profile.copy(id = id) else profile
        scheduler.schedule(saved)
        return id
    }

    suspend fun deleteProfile(profile: SyncProfile) {
        scheduler.cancel(profile.id)
        dao.delete(profile)
    }

    fun runManualSync(profile: SyncProfile) = scheduler.runManualSync(profile)

    fun cancelSync(profile: SyncProfile) = scheduler.stopRunningSync(profile)

    /** Elimina dal telefono i file già sincronizzati sull'HDD, per liberare spazio su richiesta. */
    suspend fun freeLocalSpace(profile: SyncProfile): FreeSpaceResult = withContext(Dispatchers.IO) {
        SyncEngine(context).freeLocalSpace(profile)
    }
}
