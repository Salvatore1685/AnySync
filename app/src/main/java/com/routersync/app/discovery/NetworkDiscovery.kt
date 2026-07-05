package com.routersync.app.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.routersync.app.data.RemoteProtocol
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteOrder

/** Dispositivo trovato sulla rete locale con il protocollo che sembra supportare. */
data class DiscoveredDevice(
    val ip: String,
    val hostname: String?,
    val suggestedProtocol: RemoteProtocol,
    val openPort: Int
)

/**
 * Esegue una scansione della subnet locale (classe /24) e prova a stabilire, per
 * ogni host che risponde, quale dei protocolli richiesti (SMB, FTP, WebDAV) è
 * disponibile, semplicemente verificando quali porte standard sono aperte.
 *
 * Ordine di priorità: SMB (445) > WebDAV (80/443 con path /webdav tipico dei NAS) > FTP (21).
 * SMB viene privilegiato perché è il caso più comune per un HDD condiviso da router domestici.
 */
class NetworkDiscovery(private val context: Context) {

    private val portToProtocol = linkedMapOf(
        445 to RemoteProtocol.SMB,   // SMB2/3
        139 to RemoteProtocol.SMB,   // SMB1 legacy / NetBIOS
        21 to RemoteProtocol.FTP,
        5005 to RemoteProtocol.WEBDAV, // porta comune su NAS Synology per WebDAV
        80 to RemoteProtocol.WEBDAV     // fallback generico
    )

    /** Restituisce la subnet locale, es. "192.168.1." */
    private fun getLocalSubnetPrefix(): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        val bytes = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            byteArrayOf(
                (ipInt and 0xff).toByte(),
                (ipInt shr 8 and 0xff).toByte(),
                (ipInt shr 16 and 0xff).toByte(),
                (ipInt shr 24 and 0xff).toByte()
            )
        } else {
            byteArrayOf(
                (ipInt shr 24 and 0xff).toByte(),
                (ipInt shr 16 and 0xff).toByte(),
                (ipInt shr 8 and 0xff).toByte(),
                (ipInt and 0xff).toByte()
            )
        }
        val addr = InetAddress.getByAddress(bytes).hostAddress ?: return null
        return addr.substringBeforeLast(".") + "."
    }

    /**
     * Scansiona 1-254 sulla subnet corrente. Emette i dispositivi trovati man mano
     * che vengono scoperti, così la UI può popolare la lista progressivamente.
     */
    fun scan(): kotlinx.coroutines.flow.Flow<DiscoveredDevice> = kotlinx.coroutines.flow.channelFlow {
        val prefix = getLocalSubnetPrefix() ?: return@channelFlow
        val semaphore = kotlinx.coroutines.sync.Semaphore(32) // limita i thread paralleli

        coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val ip = "$prefix$host"
                        for ((port, protocol) in portToProtocol) {
                            if (isPortOpen(ip, port, timeoutMs = 250)) {
                                val hostname = runCatching {
                                    InetAddress.getByName(ip).hostName.takeIf { it != ip }
                                }.getOrNull()
                                send(DiscoveredDevice(ip, hostname, protocol, port))
                                break // un protocollo per host è sufficiente per il suggerimento
                            }
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
            true
        }
    } catch (e: Exception) {
        false
    }
}
