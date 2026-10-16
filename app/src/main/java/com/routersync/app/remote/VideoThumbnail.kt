package com.routersync.app.remote

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.IOException

/**
 * Sorgente dati per Android che legge un video remoto a pezzi, senza scaricarlo per intero.
 *
 * Nei video girati dal telefono le informazioni per leggerli stanno spesso in fondo al file,
 * quindi non basta scaricarne l'inizio: Android chiede di volta in volta "dammi i byte da X in
 * poi". Qui ogni richiesta fuori da quanto già letto scarica un blocco di [CHUNK_BYTES] a
 * partire da quel punto e lo tiene in memoria, così le tante piccole letture vicine tra loro
 * non diventano tante richieste alla rete.
 */
private class RemoteMediaDataSource(
    private val client: RemoteClient,
    private val path: String,
    private val totalSize: Long
) : MediaDataSource() {

    private val cache = ByteArray(CHUNK_BYTES)
    private var cacheStart = 0L
    private var cacheLen = 0

    override fun getSize(): Long = totalSize

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= totalSize) return -1
        if (size <= 0) return 0
        if (position < cacheStart || position >= cacheStart + cacheLen) {
            fill(position)
            if (cacheLen == 0) return -1
        }
        val inCache = (position - cacheStart).toInt()
        val n = minOf(size, cacheLen - inCache)
        System.arraycopy(cache, inCache, buffer, offset, n)
        return n
    }

    /** Scarica un blocco di file a partire da [position] e lo mette in cache. */
    private fun fill(position: Long) {
        val want = minOf(CHUNK_BYTES.toLong(), totalSize - position).toInt()
        val input = client.openFrom(path, position)
            ?: throw IOException("Lettura a intervalli non supportata da questo server")
        input.use { stream ->
            var read = 0
            while (read < want) {
                val n = stream.read(cache, read, want - read)
                if (n < 0) break
                read += n
            }
            cacheStart = position
            cacheLen = read
        }
    }

    override fun close() {
        cacheLen = 0
    }

    private companion object {
        const val CHUNK_BYTES = 512 * 1024
    }
}

/**
 * Estrae un fotogramma (circa al secondo 1) da un video remoto da usare come anteprima, adattato
 * a stare dentro [targetPx] x [targetPx] mantenendo le proporzioni. Ritorna null se non è
 * possibile (es. FTP, server senza supporto Range, formato non leggibile): in quel caso la
 * galleria mostra semplicemente l'icona del video.
 */
fun RemoteClient.videoFrameThumbnail(path: String, size: Long, targetPx: Int): Bitmap? {
    if (size <= 0) return null
    val source = RemoteMediaDataSource(this, path, size)
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(source)
        if (Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetPx, targetPx)
        } else {
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    } finally {
        runCatching { retriever.release() }
        source.close()
    }
}
