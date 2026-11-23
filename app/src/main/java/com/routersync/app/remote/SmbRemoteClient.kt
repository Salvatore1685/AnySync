package com.routersync.app.remote

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * Client SMB/CIFS basato su jcifs-ng. Supporta SMB2/3, il protocollo più comune
 * per un HDD condiviso da un router domestico o un piccolo NAS.
 *
 * [basePath] è nel formato "smb://host/share" (senza credenziali incluse).
 */
class SmbRemoteClient(
    private val host: String,
    private val share: String,
    private val username: String,
    private val password: String
) : RemoteClient {

    private lateinit var context: CIFSContext
    private var baseContext: BaseContext? = null

    private companion object {
        /** Buffer di copia in upload: 1 MB (prima 8 KB = una richiesta SMB per ogni 8 KB). */
        const val UPLOAD_BUFFER = 1024 * 1024
        /** Suffisso dei file in caricamento: il nome definitivo compare solo a copia completata. */
        const val PART_SUFFIX = ".part"
    }

    override fun connect() {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            // Abilita compatibilità sia con SMB1 (router più vecchi) sia SMB2/3
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val base = BaseContext(PropertyConfiguration(props))
        baseContext = base
        context = base.withCredentials(
            NtlmPasswordAuthenticator("", username, password)
        )
    }

    override fun disconnect() {
        // Chiude le connessioni del pool interno (se la versione di jcifs lo supporta), così
        // dopo una caduta di rete la riconnessione riparte da una sessione pulita.
        runCatching { (baseContext as? AutoCloseable)?.close() }
        baseContext = null
    }

    /** URL di una cartella: termina sempre con "/", come richiesto da jcifs per le directory. */
    private fun dirUrl(remotePath: String): String {
        val clean = remotePath.trim('/')
        return if (clean.isEmpty()) "smb://$host/$share/" else "smb://$host/$share/$clean/"
    }

    /** URL di un file: nessuno slash finale. */
    private fun fileUrl(remotePath: String): String =
        "smb://$host/$share/${remotePath.trim('/')}"

    override fun listFiles(remotePath: String): List<RemoteEntry> {
        val dir = SmbFile(dirUrl(remotePath), context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles().filter { !it.name.trimEnd('/').endsWith(PART_SUFFIX) }.map { f ->
            val name = f.name.trimEnd('/')
            RemoteEntry(
                name = name,
                path = "${remotePath.trim('/')}/$name",
                isDirectory = f.isDirectory,
                lastModified = f.lastModified,
                size = if (f.isFile) f.length() else 0L
            )
        }
    }

    override fun mkdirs(remotePath: String) {
        val dir = SmbFile(dirUrl(remotePath), context)
        if (!dir.exists()) dir.mkdirs()
    }

    override fun exists(remotePath: String): Boolean =
        SmbFile(dirUrl(remotePath), context).exists()

    /**
     * Carica prima su "<nome>.part" e rinomina solo a copia completata: se la connessione cade
     * a metà, sull'HDD non resta mai un file troncato col nome definitivo (che la sync
     * successiva scambierebbe per "stesso nome, dimensione diversa" e duplicherebbe).
     */
    override fun upload(remotePath: String, input: InputStream, length: Long) {
        val target = SmbFile(fileUrl(remotePath), context)
        target.parent?.let { SmbFile(it, context).takeIf { p -> !p.exists() }?.mkdirs() }
        val tmp = SmbFile(fileUrl(remotePath) + PART_SUFFIX, context)
        try {
            tmp.outputStream.use { out -> input.copyTo(out, UPLOAD_BUFFER) }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            runCatching { if (tmp.exists()) tmp.delete() }
            throw e
        }
    }

    override fun download(remotePath: String, output: OutputStream) {
        val file = SmbFile(fileUrl(remotePath), context)
        file.inputStream.use { input -> input.copyTo(output, 1024 * 1024) }
    }

    override fun openFrom(remotePath: String, position: Long): InputStream? {
        val stream = SmbFile(fileUrl(remotePath), context).inputStream
        var left = position
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped <= 0) break
            left -= skipped
        }
        return if (left == 0L) {
            stream
        } else {
            stream.close()
            null
        }
    }

    override fun rename(fromPath: String, toPath: String): Boolean = try {
        SmbFile(fileUrl(fromPath), context).renameTo(SmbFile(fileUrl(toPath), context))
        true
    } catch (e: Exception) {
        false
    }

    override fun delete(remotePath: String) {
        val file = SmbFile(fileUrl(remotePath), context)
        if (file.exists()) file.delete()
    }

    /** Spazio libero sulla condivisione, in byte. Null se il server/il driver non lo supporta. */
    override fun freeSpaceBytes(): Long? = try {
        SmbFile(dirUrl(""), context).diskFreeSpace
    } catch (e: Exception) {
        null
    }
}
