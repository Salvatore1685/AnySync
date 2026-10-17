package com.routersync.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** DAO non-suspend come [RemoteFileHashDao]: va chiamato solo da thread in background (sync, galleria su IO). */
@Dao
interface DeletedFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: DeletedFileEntity)

    @Query("SELECT size FROM deleted_files WHERE profileId = :profileId AND relativePath = :relativePath LIMIT 1")
    fun findSize(profileId: Long, relativePath: String): Long?

    @Query("SELECT COUNT(*) FROM deleted_files")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM deleted_files")
    fun deleteAll()

    @Query("DELETE FROM deleted_files WHERE profileId = :profileId")
    fun deleteForProfile(profileId: Long)
}
