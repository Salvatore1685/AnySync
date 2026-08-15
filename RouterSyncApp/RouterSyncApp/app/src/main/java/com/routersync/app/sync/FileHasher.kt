package com.routersync.app.sync

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Calcolo dell'hash SHA-256 di un file, usato per riconoscere contenuti duplicati anche quando
 * nome o cartella sono diversi (es. lo stesso file caricato in passato da due telefoni diversi,
 * finito in due cartelle separate sull'HDD).
 *
 * La lettura avviene a blocchi da 8 KB per non caricare mai l'intero file in memoria, sia per i
 * file locali sul telefono sia per quelli remoti (in quel caso lo stream viene letto durante il
 * download, senza scrivere nulla su disco: vedi [discardingDigestStream]).
 */
object FileHasher {

    private const val BUFFER_SIZE = 8 * 1024

    /** Calcola l'hash SHA-256 di uno stream, chiudendolo al termine. Ritorna null se lo stream è null o in caso di errore di lettura. */
    fun sha256(input: InputStream?): String? {
        if (input == null) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { stream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    /**
     * OutputStream "a perdere": scarta i byte ricevuti ma li usa per aggiornare [digest].
     * Serve per calcolare l'hash di un file remoto sfruttando il metodo [com.routersync.app.remote.RemoteClient.download]
     * già esistente, senza doverlo prima salvare su disco.
     */
    fun discardingDigestStream(digest: MessageDigest): OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            digest.update(b.toByte())
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            digest.update(b, off, len)
        }
    }
}
