package com.routersync.app.data

import androidx.room.Entity

/**
 * File eliminato apposta dall'HDD dalla galleria dell'app (finito nel cestino): ricordarlo evita
 * che la sync lo ricarichi dal telefono, dove magari esiste ancora. Si confronta il percorso
 * relativo alla cartella della sync e la dimensione: se il file sul telefono cambia (dimensione
 * diversa) viene considerato un file nuovo e caricato normalmente.
 */
@Entity(tableName = "deleted_files", primaryKeys = ["profileId", "relativePath"])
data class DeletedFileEntity(
    val profileId: Long,
    val relativePath: String,
    val size: Long,
    val deletedAt: Long
)
