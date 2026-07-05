package com.routersync.app.remote

import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder

class WebDavRemoteClient(
    private val baseUrl: String,   // es. "http://192.168.1.10:5005/webdav"
    private val username: String,
    private val password: String
) : RemoteClient {

    private val sardine = OkHttpSardine()

    override fun connect() {
        // Se non sono state indicate credenziali, non le impostiamo affatto: alcuni server
        // WebDAV rifiutano esplicitamente un'autenticazione con utente/password vuoti,
        // mentre accettano normalmente una richiesta senza alcuna autenticazione.
        if (username.isNotBlank()) {
            sardine.setCredentials(username, password)
        }
    }

    override fun disconnect() { /* nessuna sessione persistente da chiudere */ }

    private fun encodeSegments(path: String): String =
        path.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) segment else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    private fun urlFor(remotePath: String) = baseUrl.trimEnd('/') + "/" + encodeSegments(remotePath.trim('/'))

    override fun listFiles(remotePath: String): List<RemoteEntry> {
        val resources = sardine.list(urlFor(remotePath))
        return resources
            .filter { it.href.toString().trimEnd('/') != urlFor(remotePath).trimEnd('/') } // esclude la cartella stessa
            .map { r ->
                RemoteEntry(
                    name = r.name,
                    path = "${remotePath.trim('/')}/${r.name}",
                    isDirectory = r.isDirectory,
                    lastModified = r.modified?.time ?: 0L,
                    size = r.contentLength
                )
            }
    }

    override fun mkdirs(remotePath: String) {
        val parts = remotePath.trim('/').split("/")
        var current = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            current += "/$part"
            if (!sardine.exists(urlFor(current))) {
                sardine.createDirectory(urlFor(current))
            }
        }
    }

    override fun exists(remotePath: String): Boolean = sardine.exists(urlFor(remotePath))

    override fun upload(remotePath: String, input: InputStream, length: Long) {
        mkdirs(remotePath.substringBeforeLast("/", ""))
        // Sardine richiede un byte array: per file molto grandi si dovrebbe passare
        // a una libreria WebDAV con supporto streaming, qui teniamo l'implementazione semplice.
        val bytes = input.readBytes()
        sardine.put(urlFor(remotePath), bytes)
    }

    override fun download(remotePath: String, output: OutputStream) {
        sardine.get(urlFor(remotePath)).use { input -> input.copyTo(output) }
    }

    override fun delete(remotePath: String) {
        sardine.delete(urlFor(remotePath))
    }
}
