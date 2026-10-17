package com.routersync.app.sync

import com.routersync.app.remote.RemoteClient
import com.routersync.app.remote.RemoteEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cestino sull'HDD: quando una sync bidirezionale con cancellazioni elimina un file dal lato HDD
 * (perché lo hai cancellato dal telefono) il file viene spostato in `.anysync_trash/AAAA-MM-GG/`
 * dentro la cartella di destinazione invece di sparire subito. Dopo [KEEP_DAYS] giorni la
 * cartella del giorno viene eliminata. Se lo spostamento non è possibile (es. WebDAV) il file
 * viene cancellato come prima.
 *
 * La cartella è esclusa da sync, conteggi e indice degli hash (vedi [RemoteHashIndexFile.isIndexFileName]),
 * ma la vedi nella galleria dell'HDD: per recuperare un file basta copiarlo fuori.
 */
object RemoteTrash {

    const val DIR_NAME = ".anysync_trash"
    private const val KEEP_DAYS = 7
    private val DAY_NAME = Regex("""\d{4}-\d{2}-\d{2}""")

    private fun dayFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayFolder(): String = dayFormat().format(Date())

    /**
     * Sposta un file nel cestino ([DIR_NAME]/AAAA-MM-GG/percorso relativo) dentro [root].
     * [root] e [filePath] devono essere nello stesso formato di percorso del [client]. Ritorna
     * true se spostato; false se non possibile (file fuori da [root], già nel cestino, protocollo
     * senza spostamento, errore): chi lo usa può ripiegare sulla cancellazione.
     */
    fun moveToTrash(client: RemoteClient, root: String, filePath: String): Boolean {
        val cleanRoot = root.trim('/')
        val cleanPath = filePath.trim('/')
        if (cleanRoot.isEmpty() || !cleanPath.startsWith("$cleanRoot/")) return false
        val relative = cleanPath.removePrefix("$cleanRoot/")
        if (relative.isEmpty() || relative == DIR_NAME || relative.startsWith("$DIR_NAME/")) return false
        val lead = if (root.startsWith("/")) "/" else ""
        val target = "$lead$cleanRoot/$DIR_NAME/${todayFolder()}/$relative"
        return runCatching {
            client.mkdirs(target.substringBeforeLast('/'))
            // se oggi nel cestino c'è già un file con lo stesso nome, riprova con un suffisso
            client.rename(filePath, target) || client.rename(filePath, target + "." + System.currentTimeMillis())
        }.getOrDefault(false)
    }

    /** Elimina le cartelle giornaliere del cestino più vecchie di [KEEP_DAYS] giorni. */
    fun purgeOld(client: RemoteClient, root: String) {
        val trashPath = root.trimEnd('/') + "/" + DIR_NAME
        if (!client.exists(trashPath)) return
        val cutoff = dayFormat().format(Date(System.currentTimeMillis() - KEEP_DAYS * 86_400_000L))
        for (day in client.listFiles(trashPath)) {
            if (day.isDirectory && DAY_NAME.matches(day.name) && day.name < cutoff) {
                deleteRecursively(client, day)
            }
        }
    }

    private fun deleteRecursively(client: RemoteClient, entry: RemoteEntry) {
        if (entry.isDirectory) client.listFiles(entry.path).forEach { deleteRecursively(client, it) }
        client.delete(entry.path)
    }
}
