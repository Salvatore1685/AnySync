package com.routersync.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProfileDao {

    @Query("SELECT * FROM sync_profiles ORDER BY id DESC")
    fun observeAll(): Flow<List<SyncProfile>>

    @Query("SELECT * FROM sync_profiles WHERE id = :id")
    suspend fun getById(id: Long): SyncProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SyncProfile): Long

    @Delete
    suspend fun delete(profile: SyncProfile)

    @Query("UPDATE sync_profiles SET lastSyncTimestamp = :timestamp, lastSyncStatus = :status WHERE id = :id")
    suspend fun updateSyncResult(id: Long, timestamp: Long, status: String)

    @Query("UPDATE sync_profiles SET workRequestId = :workId WHERE id = :id")
    suspend fun updateWorkRequestId(id: Long, workId: String?)

    @Query("UPDATE sync_profiles SET lastSyncManifest = :manifest WHERE id = :id")
    suspend fun updateManifest(id: Long, manifest: String?)
}
