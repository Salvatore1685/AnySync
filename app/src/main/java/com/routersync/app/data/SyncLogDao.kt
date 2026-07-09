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
