package com.routersync.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Protocolli di rete supportati per il collegamento all'HDD sul router. */
enum class RemoteProtocol {
    SMB,      // Samba / CIFS - il più comune su router con HDD condiviso (Fritz!Box, TP-Link, Synology...)
    FTP,      // File Transfer Protocol
    WEBDAV    // WebDAV - usato da molti NAS quando SMB non è disponibile
}

/** Frequenza di sincronizzazione scelta dall'utente. */
enum class ScheduleType {
    MANUAL,     // Solo tramite tasto dedicato
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY
}

/** Condizione di rete richiesta perché una sincronizzazione pianificata possa partire. */
enum class NetworkPreference {
    ANY,                  // qualsiasi rete, Wi-Fi o dati
    WIFI_ONLY,             // qualsiasi Wi-Fi (non necessariamente quello di casa)
    HOME_WIFI_ONLY,        // solo il Wi-Fi di casa riconosciuto
    MOBILE_ONLY,           // solo dati mobili (richiede IP pubblico sul router)
    HOME_WIFI_OR_MOBILE    // Wi-Fi di casa oppure dati mobili
}

/** Direzione del trasferimento file. */
enum class SyncDirection {
    UPLOAD_ONLY,      // dal telefono verso l'HDD
    DOWNLOAD_ONLY,    // dall'HDD verso il telefono
    BIDIRECTIONAL     // entrambe le direzioni, in base al timestamp più recente
}

/**
 * Rappresenta una coppia cartella-locale <-> cartella-remota con le relative
 * credenziali di connessione e il piano di sincronizzazione.
 */
@Entity(tableName = "sync_profiles")
data class SyncProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val name: String,                  // nome descrittivo scelto dall'utente

    // --- Connessione remota ---
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,              // NB: in produzione va cifrata con EncryptedSharedPreferences/Keystore
    val remoteBasePath: String,        // es. "/HDD/Backup_Telefono"

    // --- Sorgente locale ---
    val localFolderUri: String,        // Uri SAF persistito (content://...)
    val localFolderDisplayName: String,

    // --- Pianificazione ---
    val scheduleType: ScheduleType,
    val direction: SyncDirection,

    // --- Orario e giorno precisi (per DAILY/WEEKLY/MONTHLY) ---
    val scheduledHour: Int = 2,               // 0-23
    val scheduledMinute: Int = 0,              // 0-59
    val scheduledDayOfWeek: Int = java.util.Calendar.MONDAY,  // usato solo per WEEKLY (costanti java.util.Calendar)
    val scheduledDayOfMonth: Int = 1,          // usato solo per MONTHLY (1-31)

    // --- Condizioni di avvio ---
    val networkPreference: NetworkPreference = NetworkPreference.ANY,
    val requiresCharging: Boolean = false,
    val homeWifiSsid: String? = null,          // nome della rete Wi-Fi di casa, rilevato in fase di creazione

    // --- Opzioni di gestione dello spazio e delle cancellazioni ---
    val autoFreeSpaceAfterSync: Boolean = false, // elimina dal telefono i file appena caricati sull'HDD
    val mirrorDeletes: Boolean = false,          // solo per BIDIRECTIONAL: propaga le cancellazioni su entrambi i lati

    // --- Selezione fine di cosa sincronizzare ---
    val excludedPaths: String? = null, // percorsi relativi (separati da "\n") esclusi dalla sync, di default tutto è incluso

    // --- Stato ---
    val lastSyncTimestamp: Long? = null,
    val lastSyncStatus: String? = null, // "OK", "ERRORE: ...", null se mai eseguita
    val workRequestId: String? = null,  // id del WorkManager request associato, per poterlo cancellare/aggiornare
    val lastSyncManifest: String? = null // elenco (una riga per file) dei path presenti all'ultima sync riuscita, usato per capire cosa è stato cancellato quando mirrorDeletes è attivo
)
