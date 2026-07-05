package com.routersync.app.remote

import com.routersync.app.data.RemoteProtocol
import com.routersync.app.data.SyncProfile

object RemoteClientFactory {

    /**
     * [remoteBasePath] per SMB va passato come "nomeShare" (es. "HDD"); il resto del path
     * relativo alla cartella di sync viene gestito separatamente dal motore di sync.
     */
    fun create(profile: SyncProfile): RemoteClient = when (profile.protocol) {
        RemoteProtocol.SMB -> {
            val share = profile.remoteBasePath.trim('/').substringBefore("/")
            SmbRemoteClient(profile.host, share, profile.username, profile.password)
        }
        RemoteProtocol.FTP -> FtpRemoteClient(profile.host, profile.port, profile.username, profile.password)
        RemoteProtocol.WEBDAV -> WebDavRemoteClient(
            baseUrl = "http://${profile.host}:${profile.port}",
            username = profile.username,
            password = profile.password
        )
    }
}
