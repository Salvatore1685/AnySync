package com.routersync.app.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.routersync.app.data.SyncDirection
import com.routersync.app.data.SyncProfile
import com.routersync.app.remote.RemoteClient
import com.routersync.app.remote.RemoteClientFactory
import com.routersync.app.remote.RemoteEntry

data class SyncResult(
    val success: Boolean,
    val message: String,
    val filesTransferred: Int,
    val cancelled: Boolean = false,
    val updatedManifest: String? = null // da salvare sul profilo se non null (solo quando mirrorDeletes è attivo)
)

data class FreeSpaceResult(val success: Boolean, val message: String, val filesDeleted: Int, val bytesFreed: Long)

/** Callback opzionale per riportare avanzamento (usato dal Worker per aggiornare la notifica). */
typealias ProgressCallback = (current: String, done: Int, total: Int) -> Unit

/**
 * Motore di sincronizzazione. Tutto il contenuto viene replicato, struttura originale
 * inclusa, dentro un'unica cartella "All" sull'HDD (nessuna duplicazione fisica per
 * categoria: i filtri per tipo vengono calcolati al volo dall'interfaccia di navigazione).
 *
 * Due opzioni configurabili per profilo:
 * - [SyncProfile.autoFreeSpaceAfterSync]: elimina dal telefono ogni file appena caricato
 *   sull'HDD, per liberare spazio in automatico man mano che si sincronizza.
 * - [SyncProfile.mirrorDeletes] (solo per sync BIDIRECTIONAL): se un file viene cancellato
 *   da un lato, lo cancella anche dall'altro. Per distinguere "file nuovo da aggiungere" da
 *   "file cancellato da propagare", viene mantenuto un registro (manifest) di cosa era
 *   presente all'ultima sincronizzazione riuscita.
 */
class SyncEngine(private val context: Context) {

    fun run(
        profile: SyncProfile,
        onProgress: ProgressCallback? = null,
        isCancelled: () -> Boolean = { false }
    ): SyncResult {
        val client = RemoteClientFactory.create(profile)
        var transferred = 0
        var wasCancelled = false
        try {
            client.connect()

            val localRoot = DocumentFile.fromTreeUri(context, Uri.parse(profile.localFolderUri))
                ?: return SyncResult(false, "Impossibile accedere alla cartella locale: permesso mancante", 0)

            val remoteRelativeRoot = remoteRelativeRoot(profile)

            if (!client.exists(remoteRelativeRoot)) client.mkdirs(remoteRelativeRoot)
            val allPath = joinPath(remoteRelativeRoot, sanitizeFolderName(profile.name))
            if (!client.exists(allPath)) client.mkdirs(allPath)

            var updatedManifest: String? = null
            val excluded = excludedPathSet(profile.excludedPaths)

            if (profile.direction == SyncDirection.BIDIRECTIONAL && profile.mirrorDeletes) {
                // Merge a tre vie con propagazione delle cancellazioni, basato sul registro precedente
                val previousManifest = profile.lastSyncManifest
                    ?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                val newManifest = mutableSetOf<String>()
                val (count, cancelled) = mirrorBidirectionalWithDeletes(
                    client, localRoot, allPath, "", previousManifest,
                    profile.autoFreeSpaceAfterSync, excluded, onProgress, isCancelled, newManifest
                )
                transferred += count
                wasCancelled = cancelled
                if (!cancelled) updatedManifest = newManifest.joinToString("\n")
            } else {
                // Comportamento "solo aggiunta": non cancella mai nulla in automatico
                if (profile.direction == SyncDirection.UPLOAD_ONLY || profile.direction == SyncDirection.BIDIRECTIONAL) {
                    val (count, cancelled) = mirrorUpload(client, localRoot, allPath, profile.direction, profile.autoFreeSpaceAfterSync, excluded, "", onProgress, isCancelled)
                    transferred += count
                    wasCancelled = wasCancelled || cancelled
                }
                if (!wasCancelled && (profile.direction == SyncDirection.DOWNLOAD_ONLY || profile.direction == SyncDirection.BIDIRECTIONAL)) {
                    val (count, cancelled) = mirrorDownload(client, allPath, localRoot, profile.direction, onProgress, isCancelled)
                    transferred += count
                    wasCancelled = wasCancelled || cancelled
                }
            }

            return if (wasCancelled) {
                SyncResult(true, "Sincronizzazione interrotta dall'utente", transferred, cancelled = true, updatedManifest = updatedManifest)
            } else {
                SyncResult(true, "Sincronizzazione completata", transferred, updatedManifest = updatedManifest)
            }
        } catch (e: Exception) {
            return SyncResult(false, "Errore: ${e.message}", transferred)
        } finally {
            client.disconnect()
        }
    }

    /**
     * Elimina dal telefono tutti i file della cartella sincronizzata che risultano già
     * presenti (con lo stesso nome, nello stesso punto della struttura) dentro "All"
     * sull'HDD — utile per liberare spazio su richiesta dell'utente in qualsiasi momento,
     * indipendentemente dalla direzione configurata per il profilo.
     */
    fun freeLocalSpace(profile: SyncProfile): FreeSpaceResult {
        val client = RemoteClientFactory.create(profile)
        try {
            client.connect()
            val localRoot = DocumentFile.fromTreeUri(context, Uri.parse(profile.localFolderUri))
                ?: return FreeSpaceResult(false, "Impossibile accedere alla cartella locale", 0, 0)

            val remoteRelativeRoot = remoteRelativeRoot(profile)

            // Controlla sia la cartella con il nome attuale della sync, sia la vecchia
            // cartella "All": eventuali sync effettuate prima di questo aggiornamento
            // hanno ancora i file lì, ed è giusto che "Libera memoria" li trovi comunque.
            val candidatePaths = listOf(
                joinPath(remoteRelativeRoot, sanitizeFolderName(profile.name)),
                joinPath(remoteRelativeRoot, "All")
            ).distinct().filter { client.exists(it) }

            val localFileCount = countLocalFiles(localRoot)

            if (candidatePaths.isEmpty()) {
                return FreeSpaceResult(
                    true,
                    "Nessun file ancora sincronizzato su questo profilo ($localFileCount file presenti sul telefono, cartella dati non trovata sull'HDD)",
                    0, 0
                )
            }

            var deleted = 0
            var bytesFreed = 0L
            var remoteFileCount = 0
            for (path in candidatePaths) {
                val (count, bytes, remoteCount) = freeSpaceRecursive(client, localRoot, path)
                deleted += count
                bytesFreed += bytes
                remoteFileCount += remoteCount
            }

            val message = if (deleted > 0) {
                "$deleted file eliminati dal telefono"
            } else {
                // Nessuna corrispondenza trovata nemmeno case-insensitive: mostriamo alcuni
                // nomi veri da entrambi i lati, per capire dove sta la differenza esatta
                // (maiuscole/minuscole, caratteri accentati, estensioni, ecc.)
                val localSample = collectLocalNames(localRoot).take(3)
                val remoteSample = candidatePaths.flatMap { collectRemoteNames(client, it) }.take(3)
                "0 file eliminati ($localFileCount sul telefono, $remoteFileCount sull'HDD). " +
                    "Esempi telefono: ${localSample.joinToString(" | ")} — " +
                    "Esempi HDD: ${remoteSample.joinToString(" | ")}"
            }
            return FreeSpaceResult(true, message, deleted, bytesFreed)
        } catch (e: Exception) {
            return FreeSpaceResult(false, "Errore: ${e.message}", 0, 0)
        } finally {
            client.disconnect()
        }
    }

    /** Ritorna (file eliminati, byte liberati, file remoti totali incontrati) — gli ultimi due dati servono per la diagnostica mostrata all'utente. */
    private fun freeSpaceRecursive(client: RemoteClient, localDir: DocumentFile, remotePath: String): Triple<Int, Long, Int> {
        var deleted = 0
        var bytes = 0L
        if (!client.exists(remotePath)) return Triple(0, 0L, 0)
        val remoteEntries = client.listFiles(remotePath)
        // Confronto case-insensitive: alcuni filesystem sull'HDD (es. FAT32/exFAT su
        // dispositivi USB) o alcuni server SMB normalizzano le maiuscole/minuscole.
        val remoteChildren = remoteEntries.associateBy { it.name.lowercase() }
        var remoteFileCount = remoteEntries.count { !it.isDirectory }

        for (child in localDir.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val (count, b, remoteCount) = freeSpaceRecursive(client, child, joinPath(remotePath, name))
                deleted += count
                bytes += b
                remoteFileCount += remoteCount
            } else {
                val remoteMatch = remoteChildren[name.lowercase()]
                if (remoteMatch != null && !remoteMatch.isDirectory) {
                    val size = child.length()
                    if (child.delete()) {
                        deleted++
                        bytes += size
                    }
                }
            }
        }
        return Triple(deleted, bytes, remoteFileCount)
    }

    /** Conta ricorsivamente i file (non le cartelle) presenti nella cartella locale scelta per la sync. */
    private fun countLocalFiles(dir: DocumentFile): Int {
        var count = 0
        for (child in dir.listFiles()) {
            count += if (child.isDirectory) countLocalFiles(child) else 1
        }
        return count
    }

    /** Raccoglie (ricorsivamente) i nomi dei file locali, per la diagnostica in caso di mancata corrispondenza. */
    private fun collectLocalNames(dir: DocumentFile): List<String> {
        val names = mutableListOf<String>()
        for (child in dir.listFiles()) {
            if (child.isDirectory) names += collectLocalNames(child)
            else child.name?.let { names += it }
        }
        return names
    }

    /** Raccoglie (ricorsivamente) i nomi dei file remoti, per la diagnostica in caso di mancata corrispondenza. */
    private fun collectRemoteNames(client: RemoteClient, path: String): List<String> {
        val names = mutableListOf<String>()
        for (entry in client.listFiles(path)) {
            if (entry.isDirectory) names += collectRemoteNames(client, entry.path)
            else names += entry.name
        }
        return names
    }

    /** Replica ricorsivamente [localDir] dentro [remotePath] (solo aggiunte/aggiornamenti, mai cancellazioni). */
    private fun mirrorUpload(
        client: RemoteClient,
        localDir: DocumentFile,
        remotePath: String,
        direction: SyncDirection,
        autoFreeSpace: Boolean,
        excluded: Set<String>,
        localRelativePath: String,
        onProgress: ProgressCallback?,
        isCancelled: () -> Boolean
    ): Pair<Int, Boolean> {
        var transferred = 0
        if (!client.exists(remotePath)) client.mkdirs(remotePath)

        val remoteChildren: Map<String, RemoteEntry> = client.listFiles(remotePath).associateBy { it.name }
        val localChildren = localDir.listFiles()

        for (child in localChildren) {
            val name = child.name ?: continue
            val childRelativePath = if (localRelativePath.isBlank()) name else "$localRelativePath/$name"
            if (isPathExcluded(childRelativePath, excluded)) continue // escluso dall'utente in fase di selezione

            if (child.isDirectory) {
                val (count, cancelled) = mirrorUpload(client, child, joinPath(remotePath, name), direction, autoFreeSpace, excluded, childRelativePath, onProgress, isCancelled)
                transferred += count
                if (cancelled) return transferred to true
            } else {
                onProgress?.invoke(name, 0, 0)
                val remoteChild = remoteChildren[name]
                when {
                    remoteChild == null -> {
                        context.contentResolver.openInputStream(child.uri)?.use { input ->
                            client.upload(joinPath(remotePath, name), input, child.length())
                        }
                        transferred++
                        if (autoFreeSpace) child.delete()
                    }
                    remoteChild.size != child.length() -> {
                        // Stesso nome ma dimensione diversa: non è lo stesso file (es. foto
                        // omonime scattate da telefoni diversi). Manteniamo entrambe invece di
                        // sovrascrivere silenziosamente, aggiungendo un suffisso al nuovo arrivo.
                        val uniqueName = uniqueSuffixedName(name, remoteChildren.keys)
                        context.contentResolver.openInputStream(child.uri)?.use { input ->
                            client.upload(joinPath(remotePath, uniqueName), input, child.length())
                        }
                        transferred++
                        if (autoFreeSpace) child.delete()
                    }
                    direction == SyncDirection.BIDIRECTIONAL && child.lastModified() > remoteChild.lastModified -> {
                        context.contentResolver.openInputStream(child.uri)?.use { input ->
                            client.upload(joinPath(remotePath, name), input, child.length())
                        }
                        transferred++
                        if (autoFreeSpace) child.delete()
                    }
                }
                if (isCancelled()) return transferred to true
            }
        }
        return transferred to false
    }

    /** Replica ricorsivamente il contenuto remoto di [remotePath] dentro [localDir] (solo aggiunte/aggiornamenti). */
    private fun mirrorDownload(
        client: RemoteClient,
        remotePath: String,
        localDir: DocumentFile,
        direction: SyncDirection,
        onProgress: ProgressCallback?,
        isCancelled: () -> Boolean
    ): Pair<Int, Boolean> {
        var transferred = 0
        val remoteChildren = client.listFiles(remotePath)
        val localByName = localDir.listFiles().associateBy { it.name ?: "" }

        for (remote in remoteChildren) {
            if (remote.isDirectory) {
                val localSubDir = localByName[remote.name] as? DocumentFile
                    ?: localDir.createDirectory(remote.name)
                    ?: continue
                val (count, cancelled) = mirrorDownload(client, remote.path, localSubDir, direction, onProgress, isCancelled)
                transferred += count
                if (cancelled) return transferred to true
            } else {
                onProgress?.invoke(remote.name, 0, 0)
                val local = localByName[remote.name]
                val shouldDownload = local == null ||
                    (direction == SyncDirection.BIDIRECTIONAL && remote.lastModified > local.lastModified())
                if (shouldDownload) {
                    val targetFile = local ?: localDir.createFile(guessMimeType(remote.name), remote.name)
                    targetFile?.let { doc ->
                        context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                            client.download(remote.path, output)
                        }
                    }
                    transferred++
                }
                if (isCancelled()) return transferred to true
            }
        }
        return transferred to false
    }

    /**
     * Merge a tre vie: confronta lo stato attuale locale/remoto con [previousManifest] (lo
     * stato dopo l'ultima sincronizzazione riuscita) per distinguere file nuovi (da
     * aggiungere) da file cancellati (da propagare come cancellazione sull'altro lato).
     * Popola [newManifest] con lo stato finale, da salvare per il prossimo confronto.
     */
    private fun mirrorBidirectionalWithDeletes(
        client: RemoteClient,
        localDir: DocumentFile,
        remotePath: String,
        relativePrefix: String,
        previousManifest: Set<String>,
        autoFreeSpace: Boolean,
        excluded: Set<String>,
        onProgress: ProgressCallback?,
        isCancelled: () -> Boolean,
        newManifest: MutableSet<String>
    ): Pair<Int, Boolean> {
        var transferred = 0
        if (!client.exists(remotePath)) client.mkdirs(remotePath)

        val remoteChildren = client.listFiles(remotePath).associateBy { it.name }
        val localChildren = localDir.listFiles().associateBy { (it.name ?: "") }
        val allNames = remoteChildren.keys + localChildren.keys

        for (name in allNames) {
            if (name.isBlank()) continue
            val relPath = if (relativePrefix.isBlank()) name else "$relativePrefix/$name"
            if (isPathExcluded(relPath, excluded)) continue // escluso dall'utente in fase di selezione
            val localChild = localChildren[name]
            val remoteChild = remoteChildren[name]
            val wasKnownBefore = previousManifest.contains(relPath) || previousManifest.any { it.startsWith("$relPath/") }
            val isDir = localChild?.isDirectory == true || remoteChild?.isDirectory == true

            if (isDir) {
                if (remoteChild == null && wasKnownBefore) {
                    // esisteva prima solo come cartella remota nota, ora sparita dal remoto -> era stata cancellata lì: elimina anche in locale
                    localChild?.delete()
                    if (isCancelled()) return transferred to true
                    continue
                }
                if (localChild == null && wasKnownBefore) {
                    // simmetrico: cancellata in locale -> elimina anche sul remoto
                    remoteChild?.let { deleteRemoteRecursively(client, it) }
                    if (isCancelled()) return transferred to true
                    continue
                }
                val subLocalDir = (localChild as? DocumentFile) ?: localDir.createDirectory(name) ?: continue
                val (count, cancelled) = mirrorBidirectionalWithDeletes(
                    client, subLocalDir, joinPath(remotePath, name), relPath,
                    previousManifest, autoFreeSpace, excluded, onProgress, isCancelled, newManifest
                )
                transferred += count
                newManifest += relPath
                if (cancelled) return transferred to true
            } else {
                onProgress?.invoke(name, 0, 0)
                when {
                    localChild != null && remoteChild != null -> {
                        if (localChild.lastModified() > remoteChild.lastModified) {
                            context.contentResolver.openInputStream(localChild.uri)?.use { input ->
                                client.upload(joinPath(remotePath, name), input, localChild.length())
                            }
                            transferred++
                        } else if (remoteChild.lastModified > localChild.lastModified()) {
                            context.contentResolver.openOutputStream(localChild.uri)?.use { output ->
                                client.download(remoteChild.path, output)
                            }
                            transferred++
                        }
                        newManifest += relPath
                    }
                    localChild != null && remoteChild == null -> {
                        if (wasKnownBefore) {
                            // era sincronizzato, ora manca sul remoto -> cancellato lì: elimina anche in locale
                            localChild.delete()
                        } else {
                            context.contentResolver.openInputStream(localChild.uri)?.use { input ->
                                client.upload(joinPath(remotePath, name), input, localChild.length())
                            }
                            transferred++
                            newManifest += relPath
                            if (autoFreeSpace) localChild.delete()
                        }
                    }
                    remoteChild != null && localChild == null -> {
                        if (wasKnownBefore) {
                            // era sincronizzato, ora manca in locale -> cancellato lì: elimina anche sul remoto
                            client.delete(remoteChild.path)
                        } else {
                            val targetFile = localDir.createFile(guessMimeType(name), name)
                            targetFile?.let { doc ->
                                context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                                    client.download(remoteChild.path, output)
                                }
                            }
                            transferred++
                            newManifest += relPath
                        }
                    }
                }
            }
            if (isCancelled()) return transferred to true
        }
        return transferred to false
    }

    private fun deleteRemoteRecursively(client: RemoteClient, entry: RemoteEntry) {
        if (entry.isDirectory) {
            client.listFiles(entry.path).forEach { deleteRemoteRecursively(client, it) }
        }
        client.delete(entry.path)
    }

    private fun joinPath(base: String, sub: String): String = if (base.isBlank()) sub else "$base/$sub"

    /** Genera un nome tipo "foto (1).jpg" che non collida con nessuno di [existingNames]. */
    private fun uniqueSuffixedName(originalName: String, existingNames: Set<String>): String {
        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var candidate: String
        var n = 1
        do {
            candidate = "$base ($n)$ext"
            n++
        } while (existingNames.contains(candidate))
        return candidate
    }

    /** Percorso della cartella scelta in fase di configurazione, corretto per ogni protocollo (per SMB va tolto il nome della share). */
    private fun remoteRelativeRoot(profile: SyncProfile): String =
        if (profile.remoteBasePath.contains("/")) profile.remoteBasePath.substringAfter("/") else profile.remoteBasePath

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
