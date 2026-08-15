package com.routersync.app.sync

import com.routersync.app.remote.RemoteClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Persiste l'indice degli hash SHA-256 direttamente sull'HDD, in un piccolo file JSON nascosto
 * dentro la cartella di destinazione di ogni sync, in aggiunta alla cache locale nel database
 * dell'app (vedi [com.routersync.app.data.RemoteFileHashDao]).
 *
 * Motivo: la cache locale da sola si perde in caso di cambio telefono o reinstallazione
 * dell'app. Questo file invece resta sull'HDD ad aspettare — alla prima sync da un nuovo
 * dispositivo viene riletto e usato per ripopolare la cache locale, evitando di dover
 * ricalcolare da zero l'hash di ogni file già presente.
 *
 * È solo una cache di supporto: se il file manca, è corrotto o la scrittura fallisce, la sync
 * continua comunque normalmente (con hash ricalcolati al bisogno) — non deve mai bloccare nulla.
 */
object RemoteHashIndexFile {

    const val FILE_NAME = ".anysync_index.json"

    data class Entry(val path: String, val size: Long, val lastModified: Long, val sha256: String, val contentDate: Long? = null)

    /** Legge l'indice dall'HDD, se presente. Ritorna lista vuota se il file non esiste o è illeggibile/corrotto. */
    fun read(client: RemoteClient, basePath: String): List<Entry> {
        val indexPath = pathFor(basePath)
        if (!client.exists(indexPath)) return emptyList()
        return runCatching {
            val buffer = ByteArrayOutputStream()
            client.download(indexPath, buffer)
            val json = JSONArray(buffer.toString(Charsets.UTF_8.name()))
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                Entry(
                    path = obj.getString("path"),
                    size = obj.getLong("size"),
                    lastModified = obj.getLong("lastModified"),
                    sha256 = obj.getString("sha256"),
                    contentDate = if (obj.has("contentDate") && !obj.isNull("contentDate")) obj.getLong("contentDate") else null
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Scrive (sovrascrivendo) l'indice aggiornato sull'HDD. Fallisce silenziosamente in caso di errore. */
    fun write(client: RemoteClient, basePath: String, entries: Collection<Entry>) {
        runCatching {
            val json = JSONArray()
            entries.forEach { entry ->
                json.put(
                    JSONObject()
                        .put("path", entry.path)
                        .put("size", entry.size)
                        .put("lastModified", entry.lastModified)
                        .put("sha256", entry.sha256)
                        .put("contentDate", entry.contentDate ?: JSONObject.NULL)
                )
            }
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            client.upload(pathFor(basePath), ByteArrayInputStream(bytes), bytes.size.toLong())
        }
    }

    /** Vero se [name] è il file indice stesso, da escludere sempre dalla normale sincronizzazione dei contenuti. */
    fun isIndexFileName(name: String): Boolean = name == FILE_NAME

    private fun pathFor(basePath: String): String = if (basePath.isBlank()) FILE_NAME else "$basePath/$FILE_NAME"
}
