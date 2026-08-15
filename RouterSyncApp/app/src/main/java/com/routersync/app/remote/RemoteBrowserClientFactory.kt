package com.routersync.app.remote

import com.routersync.app.data.RemoteProtocol

/**
 * Costruisce un [RemoteClient] pensato per la libera navigazione (wizard e galleria),
 * a differenza di [RemoteClientFactory] che costruisce client già "ancorati" alla
 * cartella di sincronizzazione di un profilo salvato.
 */
object RemoteBrowserClientFactory {
    fun create(
        protocol: RemoteProtocol,
        host: String,
        port: Int,
        username: String,
        password: String
    ): RemoteClient = when (protocol) {
        RemoteProtocol.SMB -> SmbBrowserClient(host, username, password)
        RemoteProtocol.FTP -> FtpRemoteClient(host, port, username, password)
        RemoteProtocol.WEBDAV -> WebDavRemoteClient(
            baseUrl = "http://$host:$port",
            username = username,
            password = password
        )
    }
}
