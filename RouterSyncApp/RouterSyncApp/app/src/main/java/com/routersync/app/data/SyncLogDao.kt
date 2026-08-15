package com.routersync.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert
    suspend fun insert(entry: SyncLogEntry)

    @Query("SELECT * FROM sync_log_entries WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    fun observeForProfile(profileId: Long, limit: Int = 50): Flow<List<SyncLogEntry>>

    /** Ultimi tentativi falliti (non riusciti e non semplicemente interrotti dall'utente), su TUTTI i profili — per la sezione "Errori recenti" nelle Impostazioni. */
    @Query("SELECT * FROM sync_log_entries WHERE success = 0 AND cancelled = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentErrors(limit: Int = 30): Flow<List<SyncLogEntry>>

    /** Numero totale di sincronizzazioni riuscite su tutti i profili, per le statistiche d'uso. */
    @Query("SELECT COUNT(*) FROM sync_log_entries WHERE success = 1")
    fun observeSuccessfulSyncCount(): Flow<Int>

    /** Somma di tutti i file trasferiti con successo su tutti i profili, per le statistiche d'uso. */
    @Query("SELECT COALESCE(SUM(filesTransferred), 0) FROM sync_log_entries WHERE success = 1")
    fun observeTotalFilesTransferred(): Flow<Int>

    /** Tiene solo le ultime [keep] voci per profilo, per non far crescere il database all'infinito. */
    @Query("""
        DELETE FROM sync_log_entries
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM sync_log_entries WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :keep
        )
    """)
    suspend fun trimOldEntries(profileId: Long, keep: Int = 50)

    @Query("DELETE FROM sync_log_entries WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}
