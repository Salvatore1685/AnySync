package com.routersync.app.remote

import java.io.InputStream
import java.io.OutputStream

/** Metadati minimi di un file/cartella remoti, usati dal motore di sync per il confronto. */
data class RemoteEntry(
    val name: String,
    val path: String,          // path completo remoto
    val isDirectory: Boolean,
    val lastModified: Long,    // epoch millis
    val size: Long
)

/**
 * Astrazione unica sopra SMB, FTP e WebDAV: il motore di sincronizzazione lavora
 * sempre contro questa interfaccia, senza sapere quale protocollo è in uso.
 */
interface RemoteClient {

    /** Apre la connessione. Deve essere chiamato prima di ogni altra operazione. */
    fun connect()

    /** Chiude la connessione e libera le risorse. */
    fun disconnect()

    /** Elenca il contenuto di una cartella remota (path relativo alla root configurata). */
    fun listFiles(remotePath: String): List<RemoteEntry>

    /** Crea una cartella remota (e le eventuali cartelle padre mancanti). */
    fun mkdirs(remotePath: String)

    /** Verifica se un path remoto esiste. */
    fun exists(remotePath: String): Boolean

    /** Carica un file dal telefono verso il remoto. */
    fun upload(remotePath: String, input: InputStream, length: Long)

    /** Scarica un file dal remoto verso un OutputStream locale. */
    fun download(remotePath: String, output: OutputStream)

    /** Elimina un file remoto (usato solo se in futuro si vorrà una sync "a specchio"). */
    fun delete(remotePath: String)

    /** Spazio libero (in byte) sulla condivisione remota, se il protocollo lo supporta. Null altrimenti. */
    fun freeSpaceBytes(): Long? = null

    /**
     * Apre un file remoto a partire dal byte [position], senza scaricare quello che c'è prima.
     * Serve a leggere solo i pezzi che interessano di un video (per l'anteprima). Null se il
     * protocollo non lo supporta (es. FTP): chi lo usa deve gestire il caso.
     */
    fun openFrom(remotePath: String, position: Long): InputStream? = null

    /**
     * Sposta/rinomina un file remoto. Ritorna true se riuscito, false se non supportato o fallito
     * (chi lo usa deve prevedere un'alternativa). Serve al cestino sull'HDD.
     */
    fun rename(fromPath: String, toPath: String): Boolean = false
}
