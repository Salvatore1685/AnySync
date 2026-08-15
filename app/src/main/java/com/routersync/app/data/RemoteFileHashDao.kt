package com.routersync.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO volutamente non-suspend: viene chiamato solo da [com.routersync.app.sync.SyncEngine],
 * che gira già su un thread in background (Dispatchers.IO) durante l'intera sincronizzazione,
 * mai dal thread principale.
 */
@Dao
interface RemoteFileHashDao {

    @Query("SELECT * FROM remote_file_hashes WHERE profileId = :profileId AND remotePath = :remotePath LIMIT 1")
    fun find(profileId: Long, remotePath: String): RemoteFileHashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: RemoteFileHashEntity)

    @Query("DELETE FROM remote_file_hashes WHERE profileId = :profileId")
    fun deleteForProfile(profileId: Long)

    /** Tutte le voci in cache per un profilo (usato per riscrivere l'indice completo sull'HDD, data di scatto inclusa). */
    @Query("SELECT * FROM remote_file_hashes WHERE profileId = :profileId")
    fun getAllForProfile(profileId: Long): List<RemoteFileHashEntity>

    /** Imposta la data di scatto solo se non è già nota, per non perdere un valore migliore già calcolato. */
    @Query("UPDATE remote_file_hashes SET contentDate = :contentDate WHERE profileId = :profileId AND remotePath = :remotePath AND contentDate IS NULL")
    fun setContentDateIfMissing(profileId: Long, remotePath: String, contentDate: Long)
}
