package com.routersync.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Salva la configurazione delle sync in un file JSON nella cartella scelta dall'utente, sempre
 * con lo stesso nome (viene sovrascritto a ogni backup). Stesso formato di Esporta/Importa.
 */
object AutoBackup {
    const val FILE_NAME = "anysync-backup.json"

    /** Esegue il backup ora. Ritorna il numero di sync salvate; lancia un'eccezione con un messaggio comprensibile se fallisce. */
    suspend fun run(context: Context): Int {
        val appContext = context.applicationContext
        val settings = AppSettings(appContext)
        val folder = settings.autoBackupFolderUri ?: throw IllegalStateException("Nessuna cartella scelta")
        val dir = DocumentFile.fromTreeUri(appContext, Uri.parse(folder))
            ?: throw IllegalStateException("Cartella non accessibile: sceglila di nuovo")
        if (!dir.canWrite()) throw IllegalStateException("Permesso di scrittura perso: sceglila di nuovo")

        val profiles = AppDatabase.getInstance(appContext).syncProfileDao().getAllOnce()
        // Con zero sync non si scrive nulla: eviti di sovrascrivere un buon backup con uno vuoto.
        if (profiles.isEmpty()) throw IllegalStateException("Nessuna sync da salvare")

        val json = ProfileBackup.export(profiles)
        val file = dir.findFile(FILE_NAME)
            ?: dir.createFile("application/json", FILE_NAME)
            ?: throw IllegalStateException("Impossibile creare il file nella cartella")
        val out = appContext.contentResolver.openOutputStream(file.uri, "wt")
            ?: throw IllegalStateException("Impossibile scrivere il file")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }

        settings.lastAutoBackupTimestamp = System.currentTimeMillis()
        return profiles.size
    }
}
