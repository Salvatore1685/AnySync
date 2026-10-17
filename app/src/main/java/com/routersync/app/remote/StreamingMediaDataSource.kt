package com.routersync.app.remote

import android.media.MediaDataSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.abs

/**
 * Sorgente dati per la riproduzione in streaming di un video remoto.
 *
 * Android, con una sorgente personalizzata, non bufferizza in anticipo: ogni lettura del video
 * aspetta la rete. Con i video del telefono (bitrate alto) questo li fa bloccare dopo pochi
 * secondi. Qui invece alcuni thread in background scaricano in anticipo i blocchi da
 * [blockBytes] davanti alla posizione di riproduzione (fino a [aheadBlocks]), in parallelo e
 * ognuno con la propria lettura, e [readAt] pesca solo dalla memoria, aspettando soltanto se il
 * blocco non è ancora arrivato. In caso di salto avanti/indietro la finestra si sposta da sola.
 *
 * [close] non blocca mai: segnala solo la chiusura ai thread e libera la memoria.
 */
class StreamingMediaDataSource(
    private val client: RemoteClient,
    private val path: String,
    private val totalSize: Long,
    private val blockBytes: Int = 1024 * 1024,
    private val aheadBlocks: Int = 20,
    private val maxBlocks: Int = 32,
    workerCount: Int = 3
) : MediaDataSource() {

    private val blockCount = ((totalSize + blockBytes - 1) / blockBytes).toInt()
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val blocks = HashMap<Int, ByteArray>()
    private val inflight = HashSet<Int>()
    private val failures = HashMap<Int, Int>()
    private var readBlock = 0
    private var closed = false

    init {
        repeat(workerCount) { i ->
            thread(start = true, isDaemon = true, name = "anysync-stream-$i") { workerLoop() }
        }
    }

    override fun getSize(): Long = totalSize

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= totalSize) return -1
        if (size <= 0) return 0
        val index = (position / blockBytes).toInt()
        val block = awaitBlock(index) ?: return -1
        val inBlock = (position - index.toLong() * blockBytes).toInt()
        val n = minOf(size, block.size - inBlock)
        if (n <= 0) return -1
        System.arraycopy(block, inBlock, buffer, offset, n)
        return n
    }

    /** Aspetta che il blocco [index] sia in memoria. Null se la sorgente è stata chiusa. */
    private fun awaitBlock(index: Int): ByteArray? {
        lock.lock()
        try {
            readBlock = index
            changed.signalAll() // fa ricalcolare la finestra ai thread (utile dopo un salto)
            var remaining = TimeUnit.SECONDS.toNanos(READ_TIMEOUT_SECONDS)
            while (true) {
                if (closed) return null
                val ready = blocks[index]
                if (ready != null) return ready
                if ((failures[index] ?: 0) >= MAX_RETRIES) throw IOException("Lettura dall'HDD non riuscita")
                if (remaining <= 0L) throw IOException("Tempo scaduto nella lettura dall'HDD")
                remaining = changed.awaitNanos(remaining)
            }
        } finally {
            lock.unlock()
        }
    }

    /** Primo blocco della finestra davanti alla posizione che non c'è e non è già in scaricamento. Chiamare col lock preso. */
    private fun nextWanted(): Int {
        val last = minOf(blockCount, readBlock + aheadBlocks)
        for (i in readBlock until last) {
            if (!blocks.containsKey(i) && !inflight.contains(i) && (failures[i] ?: 0) < MAX_RETRIES) return i
        }
        return -1
    }

    private fun workerLoop() {
        while (true) {
            var index = -1
            lock.withLock {
                while (!closed) {
                    val wanted = nextWanted()
                    if (wanted >= 0) {
                        inflight.add(wanted)
                        index = wanted
                        break
                    }
                    changed.await()
                }
            }
            if (index < 0) return // sorgente chiusa

            val data = try {
                fetchBlock(index)
            } catch (e: Exception) {
                null
            }

            lock.withLock {
                inflight.remove(index)
                if (data != null) {
                    if (!closed) {
                        blocks[index] = data
                        evictFarBlocks()
                    }
                } else {
                    failures[index] = (failures[index] ?: 0) + 1
                }
                changed.signalAll()
            }
            if (data == null) {
                try { Thread.sleep(300) } catch (e: InterruptedException) { return }
            }
        }
    }

    /** Scarica un blocco intero con una lettura propria, aperta a partire dal suo primo byte. */
    private fun fetchBlock(index: Int): ByteArray {
        val start = index.toLong() * blockBytes
        val want = minOf(blockBytes.toLong(), totalSize - start).toInt()
        val data = ByteArray(want)
        val input = client.openFrom(path, start)
            ?: throw IOException("Lettura a intervalli non supportata da questo server")
        input.use { stream ->
            var read = 0
            while (read < want) {
                val n = stream.read(data, read, want - read)
                if (n < 0) break
                read += n
            }
            if (read < want) throw IOException("Blocco incompleto")
        }
        return data
    }

    /** Tiene in memoria solo i [maxBlocks] blocchi più vicini alla posizione corrente. Chiamare col lock preso. */
    private fun evictFarBlocks() {
        while (blocks.size > maxBlocks) {
            val farthest = blocks.keys.maxByOrNull { abs(it - readBlock) } ?: return
            blocks.remove(farthest)
        }
    }

    override fun close() {
        lock.withLock {
            closed = true
            blocks.clear()
            changed.signalAll()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val READ_TIMEOUT_SECONDS = 60L
    }
}
