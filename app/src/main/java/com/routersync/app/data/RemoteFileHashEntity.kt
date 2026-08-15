package com.routersync.app.data

import androidx.room.Entity
import androidx.room.Index

/**
 * Cache dell'hash SHA-256 di ogni file già presente nella cartella di destinazione remota di un
 * profilo. Permette al motore di sync di riconoscere contenuti duplicati anche quando si trovano
 * in una sottocartella o con un nome diverso da quello del file locale (es. lo stesso file
 * caricato in passato da un altro telefono).
 *
 * L'hash viene ricalcolato solo quando [size] o [lastModified] del file remoto risultano diversi
 * da quelli salvati qui, per evitare di dover rileggere tutto il contenuto dell'HDD a ogni
 * sincronizzazione.
 */
@Entity(
    tableName = "remote_file_hashes",
    primaryKeys = ["profileId", "remotePath"],
    indices = [Index(value = ["profileId", "sha256"])]
)
data class RemoteFileHashEntity(
    val profileId: Long,
    val remotePath: String,
    val size: Long,
    val lastModified: Long,
    val sha256: String,
    /** Data di scatto/creazione originale del contenuto (da EXIF o dal file sul telefono prima
     * dell'upload), usata per ordinare cronologicamente invece che per data di caricamento
     * sull'HDD. Null per i file caricati prima di questa funzione o senza data disponibile. */
    val contentDate: Long? = null
)
