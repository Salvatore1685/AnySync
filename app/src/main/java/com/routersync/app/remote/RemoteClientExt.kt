package com.routersync.app.remote

import java.io.ByteArrayOutputStream

/** Scarica un file remoto interamente in memoria. Da usare solo per file di dimensioni contenute (es. anteprime foto). */
fun RemoteClient.downloadBytes(path: String): ByteArray {
    val out = ByteArrayOutputStream()
    download(path, out)
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
