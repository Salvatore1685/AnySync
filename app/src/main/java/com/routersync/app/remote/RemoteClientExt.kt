package com.routersync.app.remote

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Scarica un file remoto interamente in memoria. Da usare solo per file di dimensioni contenute (es. anteprime foto). */
fun RemoteClient.downloadBytes(path: String): ByteArray {
    val out = ByteArrayOutputStream()
    download(path, out)
    return out.toByteArray()
}

/** Segnale interno per interrompere un download una volta letti abbastanza byte. */
private class StopDownload : RuntimeException()

/**
 * Scarica solo i primi [maxBytes] di un file remoto e interrompe subito il trasferimento, senza
 * leggere il resto. Serve per estrarre l'anteprima incorporata in una foto (sta nei primi KB)
 * senza scaricare decine di MB. NON usare con FTP: interrompere a metà lascia la connessione
 * in uno stato incoerente.
 */
fun RemoteClient.downloadHead(path: String, maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val limited = object : OutputStream() {
        override fun write(b: Int) {
            if (out.size() >= maxBytes) throw StopDownload()
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val remaining = maxBytes - out.size()
            if (remaining <= 0) throw StopDownload()
            out.write(b, off, minOf(len, remaining))
            if (len >= remaining) throw StopDownload()
        }
    }
    try {
        download(path, limited)
    } catch (_: StopDownload) {
        // Voluto: abbiamo già i byte che ci servivano
    }
    return out.toByteArray()
}

/** Estensioni comuni di immagine, video e audio, usate per la visualizzazione a griglia stile galleria. */
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "avi", "webm", "3gp", "m4v", "wmv")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus")

fun RemoteEntry.isImage(): Boolean =
    !isDirectory && name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

fun RemoteEntry.isVideo(): Boolean =
    !isDirectory && name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

fun RemoteEntry.isAudio(): Boolean =
    !isDirectory && name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
