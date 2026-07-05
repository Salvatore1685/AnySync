package com.routersync.app.remote

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.InputStream
import java.io.OutputStream

class FtpRemoteClient(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) : RemoteClient {

    private val ftp = FTPClient()

    override fun connect() {
        ftp.connect(host, port)
        // Molti server FTP rifiutano un utente vuoto: se non è stato indicato nulla,
        // usiamo la convenzione standard per l'accesso anonimo.
        if (username.isBlank()) {
            ftp.login("anonymous", "anonymous@")
        } else {
            ftp.login(username, password)
        }
        ftp.enterLocalPassiveMode() // necessario nella maggior parte delle reti domestiche/NAT
        ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
    }

    override fun disconnect() {
        runCatching {
            if (ftp.isConnected) {
                ftp.logout()
                ftp.disconnect()
            }
        }
    }

    override fun listFiles(remotePath: String): List<RemoteEntry> {
        val files: Array<FTPFile> = ftp.listFiles(remotePath) ?: emptyArray()
        return files.filter { it.name != "." && it.name != ".." }.map { f ->
            RemoteEntry(
                name = f.name,
                path = "${remotePath.trimEnd('/')}/${f.name}",
                isDirectory = f.isDirectory,
                lastModified = f.timestamp?.timeInMillis ?: 0L,
                size = f.size
            )
        }
    }

    override fun mkdirs(remotePath: String) {
        val parts = remotePath.trim('/').split("/")
        var current = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            current += "/$part"
            if (!ftp.changeWorkingDirectory(current)) {
                ftp.makeDirectory(current)
            }
        }
    }

    override fun exists(remotePath: String): Boolean {
        val parent = remotePath.substringBeforeLast("/", "/")
        val name = remotePath.substringAfterLast("/")
        val files = ftp.listNames(parent) ?: return false
        return files.any { it.substringAfterLast("/") == name }
    }

    override fun upload(remotePath: String, input: InputStream, length: Long) {
        mkdirs(remotePath.substringBeforeLast("/", ""))
        ftp.storeFile(remotePath, input)
    }

    override fun download(remotePath: String, output: OutputStream) {
        ftp.retrieveFile(remotePath, output)
    }

    override fun delete(remotePath: String) {
        ftp.deleteFile(remotePath)
    }
}
