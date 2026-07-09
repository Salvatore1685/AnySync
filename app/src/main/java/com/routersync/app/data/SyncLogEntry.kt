package com.routersync.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Una singola voce di cronologia: il risultato di un tentativo di sincronizzazione. */
@Entity(tableName = "sync_log_entries")
data class SyncLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val timestamp: Long,
    val success: Boolean,
    val cancelled: Boolean,
    val message: String,
    val filesTransferred: Int
)
