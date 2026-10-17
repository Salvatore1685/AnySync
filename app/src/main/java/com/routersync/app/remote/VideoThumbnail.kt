package com.routersync.app.remote

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.IOException
import java.io.InputStream

/**
 * Sorgente dati per Android che legge un video remoto a pezzi, senza scaricarlo per intero. Serve
 * sia per l'anteprima (poche letture sparse) sia per la riproduzione in streaming (lettura
 * quasi sequenziale).
 *
 * Nei video girati dal telefono le informazioni per leggerli stanno spesso in fondo al file,
 * quindi Android chiede di volta in volta "dammi i byte da X in poi". Ogni richiesta fuori da
 * quanto già letto scarica un blocco di [chunkBytes] e lo tiene in memoria. Se la richiesta
 * successiva riparte esattamente da dove il blocco precedente era finito (riproduzione normale)
 * la connessione già aperta viene riusata, senza riaprire il file a ogni blocco.
 */
class RemoteMediaDataSource(
    private val client: RemoteClient,
    private val path: String,
    private val totalSize: Long,
    private val chunkBytes: Int = 512 * 1024
) : MediaDataSource() {

    private val cache = ByteArray(chunkBytes)
    private var cacheStart = 0L
    private var cacheLen = 0
    private var stream: InputStream? = null
    private var streamPos = -1L // posizione del prossimo byte che [stream] fornirebbe

    override fun getSize(): Long = totalSize

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= totalSize) return -1
        if (size <= 0) return 0
        if (position < cacheStart || position >= cacheStart + cacheLen) {
            try {
                fill(position)
            } catch (e: IOException) {
                // una connessione rimasta ferma troppo (video in pausa) può essersi chiusa: un secondo tentativo la riapre
                closeStream()
                fill(position)
            }
            if (cacheLen == 0) return -1
        }
        val inCache = (position - cacheStart).toInt()
        val n = minOf(size, cacheLen - inCache)
        System.arraycopy(cache, inCache, buffer, offset, n)
        return n
    }

    /** Scarica un blocco di file a partire da [position] e lo mette in cache. */
    private fun fill(position: Long) {
        val want = minOf(chunkBytes.toLong(), totalSize - position).toInt()
        val input: InputStream = stream?.takeIf { streamPos == position } ?: run {
            closeStream()
            (client.openFrom(path, position)
                ?: throw IOException("Lettura a intervalli non supportata da questo server"))
                .also { stream = it }
        }
        var read = 0
        try {
            while (read < want) {
                val n = input.read(cache, read, want - read)
                if (n < 0) break
                read += n
            }
        } catch (e: IOException) {
            closeStream()
            throw e
        }
        cacheStart = position
        cacheLen = read
        streamPos = position + read
        if (read < want) closeStream()
    }

    private fun closeStream() {
        runCatching { stream?.close() }
        stream = null
        streamPos = -1L
    }

    @Synchronized
    override fun close() {
        cacheLen = 0
        closeStream()
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
