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
 * Variante "browsing" del client SMB: a differenza di [SmbRemoteClient] (che punta a una
 * share fissa, usata dal motore di sincronizzazione), questo client naviga a partire dalla
 * radice del server (smb://host/). Con un path vuoto elenca le condivisioni disponibili;
 * il primo segmento di ogni path è quindi il nome della share, i successivi sono sottocartelle.
 *
 * Usato solo per il wizard (scelta/creazione cartella di destinazione) e per la galleria dei
 * file sincronizzati, dove l'utente vuole poter esplorare liberamente l'HDD.
 */
class SmbBrowserClient(
    private val host: String,
    private val username: String,
    private val password: String
) : RemoteClient {

    private lateinit var context: CIFSContext

    override fun connect() {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val baseContext = BaseContext(PropertyConfiguration(props))
        context = baseContext.withCredentials(NtlmPasswordAuthenticator("", username, password))
    }

    override fun disconnect() { /* stateless, nessuna sessione da chiudere esplicitamente */ }

    /** URL di una cartella (con slash finale, richiesto da jcifs per le directory). */
    private fun dirUrl(path: String): String {
        val clean = path.trim('/')
        return if (clean.isEmpty()) "smb://$host/" else "smb://$host/$clean/"
    }

    /** URL di un file (senza slash finale). */
    private fun fileUrl(path: String): String = "smb://$host/${path.trim('/')}"

    override fun listFiles(remotePath: String): List<RemoteEntry> {
        val dir = SmbFile(dirUrl(remotePath), context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles().map { f ->
            val name = f.name.trimEnd('/')
            RemoteEntry(
                name = name,
                path = if (remotePath.isBlank()) name else "${remotePath.trim('/')}/$name",
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

    override fun exists(remotePath: String): Boolean = SmbFile(dirUrl(remotePath), context).exists()

    override fun upload(remotePath: String, input: InputStream, length: Long) {
        val file = SmbFile(fileUrl(remotePath), context)
        input.copyTo(file.outputStream)
    }

    override fun download(remotePath: String, output: OutputStream) {
        val file = SmbFile(fileUrl(remotePath), context)
        file.inputStream.use { it.copyTo(output) }
    }

    override fun delete(remotePath: String) {
        val file = SmbFile(fileUrl(remotePath), context)
        if (file.exists()) file.delete()
    }
}
