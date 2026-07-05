package com.routersync.app.remote

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
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

    override fun connect() {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            // Abilita compatibilità sia con SMB1 (router più vecchi) sia SMB2/3
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val baseContext = BaseContext(PropertyConfiguration(props))
        context = baseContext.withCredentials(
            NtlmPasswordAuthenticator("", username, password)
        )
    }

    override fun disconnect() {
        // jcifs-ng non richiede una disconnessione esplicita: le SmbFile sono stateless
        // e la sessione viene chiusa automaticamente dal pool interno.
    }

    /**
     * Codifica ogni singolo segmento del percorso (per gestire correttamente spazi e
     * caratteri speciali nei nomi di cartelle/file, es. "Salvatore Backup"), preservando
     * gli slash come separatori. Senza questa codifica, jcifs può interpretare male
     * l'URL smb:// quando un nome contiene uno spazio non incapsulato, con il rischio di
     * unire per errore il nome di una cartella a quello del file al suo interno.
     */
    private fun encodeSegments(path: String): String =
        path.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) segment else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    /** URL di una cartella: termina sempre con "/", come richiesto da jcifs per le directory. */
    private fun dirUrl(remotePath: String): String {
        val clean = encodeSegments(remotePath.trim('/'))
        return if (clean.isEmpty()) "smb://$host/$share/" else "smb://$host/$share/$clean/"
    }

    /** URL di un file: nessuno slash finale. */
    private fun fileUrl(remotePath: String): String =
        "smb://$host/$share/${encodeSegments(remotePath.trim('/'))}"

    override fun listFiles(remotePath: String): List<RemoteEntry> {
        val dir = SmbFile(dirUrl(remotePath), context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles().map { f ->
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

    override fun upload(remotePath: String, input: InputStream, length: Long) {
        val file = SmbFile(fileUrl(remotePath), context)
        file.parent?.let { SmbFile(it, context).takeIf { p -> !p.exists() }?.mkdirs() }
        file.outputStream.use { out -> input.copyTo(out) }
    }

    override fun download(remotePath: String, output: OutputStream) {
        val file = SmbFile(fileUrl(remotePath), context)
        file.inputStream.use { input -> input.copyTo(output) }
    }

    override fun delete(remotePath: String) {
        val file = SmbFile(fileUrl(remotePath), context)
        if (file.exists()) file.delete()
    }
}
