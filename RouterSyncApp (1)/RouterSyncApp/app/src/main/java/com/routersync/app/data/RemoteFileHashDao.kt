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
}
