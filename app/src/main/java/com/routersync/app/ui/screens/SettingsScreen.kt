package com.routersync.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routersync.app.data.AppDatabase
import androidx.documentfile.provider.DocumentFile
import com.routersync.app.data.AppSettings
import com.routersync.app.data.AutoBackup
import com.routersync.app.data.ProfileBackup
import com.routersync.app.data.SyncProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val repository = remember { SyncProfileRepository(context.applicationContext) }

    var notifEnabled by remember { mutableStateOf(settings.lowSpaceNotificationsEnabled) }
    var snoozeUntil by remember { mutableStateOf(settings.lowSpaceNotificationSnoozeUntil) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var staleEnabled by remember { mutableStateOf(settings.staleSyncNotificationsEnabled) }
    var autoBackupEnabled by remember { mutableStateOf(settings.autoBackupEnabled) }
    var backupFolderUri by remember { mutableStateOf(settings.autoBackupFolderUri) }
    var lastAutoBackup by remember { mutableStateOf(settings.lastAutoBackupTimestamp) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val deletedCount by db.deletedFileDao().observeCount().collectAsState(initial = 0)
    val successfulSyncs by db.syncLogDao().observeSuccessfulSyncCount().collectAsState(initial = 0)
    val totalFilesTransferred by db.syncLogDao().observeTotalFilesTransferred().collectAsState(initial = 0)
    val recentErrors by db.syncLogDao().observeRecentErrors().collectAsState(initial = emptyList())
    val profiles by db.syncProfileDao().observeAll().collectAsState(initial = emptyList())
    val profileNamesById = remember(profiles) { profiles.associate { it.id to it.name } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val allProfiles = db.syncProfileDao().getAllOnce()
                val json = ProfileBackup.export(allProfiles)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                allProfiles.size
            }.onSuccess { count ->
                statusMessage = "Backup esportato: $count sync salvate"
            }.onFailure {
                statusMessage = "Errore durante l'esportazione: ${it.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("File non leggibile")
                val imported = ProfileBackup.import(text)
                imported.forEach { repository.saveProfile(it) }
                imported.size
            }.onSuccess { count ->
                statusMessage = if (count > 0) "$count sync importate come nuovi profili" else "Nessuna sync valida trovata nel file"
            }.onFailure {
                statusMessage = "Errore durante l'importazione: ${it.message}"
            }
        }
    }

    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        settings.autoBackupFolderUri = uri.toString()
        settings.autoBackupEnabled = true
        backupFolderUri = uri.toString()
        autoBackupEnabled = true
        scope.launch {
            runCatching { AutoBackup.run(context) }
                .onSuccess { count -> backupStatus = "Backup creato: $count sync salvate" }
                .onFailure { backupStatus = "Backup non riuscito: ${it.message}" }
            lastAutoBackup = settings.lastAutoBackupTimestamp
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // --- Info app ---
            SectionTitle("AnySync")
            Text(
                "Versione ${appVersionName(context)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionDivider()

            // --- Statistiche d'uso ---
            SectionTitle("Statistiche d'uso")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "Sync configurate", value = profiles.size.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "Sincronizzazioni riuscite", value = successfulSyncs.toString(), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            StatCard(label = "File trasferiti in totale", value = totalFilesTransferred.toString(), modifier = Modifier.fillMaxWidth())

            SectionDivider()

            // --- Backup / ripristino configurazione ---
            SectionTitle("Backup configurazione")
            Text(
                "Esporta tutte le sync (server, cartelle, pianificazioni) in un file da conservare o " +
                    "trasferire su un altro dispositivo. Il file contiene le password in chiaro: conservalo con cura.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val fileName = "anysync_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                        exportLauncher.launch(fileName)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Esporta")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importa")
                }
            }
            Text(
                "Le sync importate vengono aggiunte come nuovi profili (non sovrascrivono quelli esistenti).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            statusMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(msg, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionDivider()

            // --- File eliminati dall'HDD ---
            SectionTitle("File eliminati dall'HDD")
            Text(
                "Quando elimini un file dalla galleria dell'HDD, la sync ricorda che l'hai tolto apposta e " +
                    "non lo ricarica dal telefono. File ricordati: $deletedCount.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (deletedCount > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { db.deletedFileDao().deleteAll() } }
                        statusMessage = "Elenco azzerato: alla prossima sync i file ancora sul telefono verranno ricaricati"
                    }
                }) { Text("Dimentica l'elenco (ricarica alla prossima sync)") }
            }
            SectionDivider()

            // --- Avviso sync ferma ---
            SectionTitle("Avviso se una sync si ferma")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Avvisami", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ricevi una notifica se una sync pianificata non riesce da più del previsto " +
                            "(per una giornaliera: oltre un giorno e mezzo). Controllo ogni 12 ore, al massimo un avviso al giorno per sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = staleEnabled,
                    onCheckedChange = {
                        staleEnabled = it
                        settings.staleSyncNotificationsEnabled = it
                    }
                )
            }

            SectionDivider()

            // --- Backup automatico ---
            SectionTitle("Backup automatico")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Salva le sync ogni settimana", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Scrive il file ${AutoBackup.FILE_NAME} nella cartella scelta (sovrascrivendo il precedente). " +
                            "Contiene le password in chiaro: scegli una cartella privata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { on ->
                        if (on && backupFolderUri == null) {
                            backupFolderLauncher.launch(null)
                        } else {
                            autoBackupEnabled = on
                            settings.autoBackupEnabled = on
                        }
                    }
                )
            }
            backupFolderUri?.let { uriString ->
                val folderName = remember(uriString) {
                    runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name }.getOrNull()
                        ?: "Cartella scelta"
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cartella: $folderName",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (lastAutoBackup > 0L)
                        "Ultimo backup: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastAutoBackup))}"
                    else "Nessun backup ancora eseguito",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { backupFolderLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
                    Text(if (backupFolderUri == null) "Scegli cartella" else "Cambia cartella")
                }
                if (backupFolderUri != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching { AutoBackup.run(context) }
                                    .onSuccess { count -> backupStatus = "Backup creato: $count sync salvate" }
                                    .onFailure { backupStatus = "Backup non riuscito: ${it.message}" }
                                lastAutoBackup = settings.lastAutoBackupTimestamp
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Esegui ora") }
                }
            }
            backupStatus?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(msg, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionDivider()

            // --- Notifiche spazio HDD ---
            SectionTitle("Notifiche spazio HDD in esaurimento")
            Text(
                "La soglia di avviso si imposta singolarmente per ogni sync (in creazione o modifica), " +
                    "dato che ogni HDD può avere una capacità diversa. Qui scegli solo se e quando ricevere " +
                    "una notifica quando una sync raggiunge la propria soglia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Notifiche attive", style = MaterialTheme.typography.bodyLarge)
                    Text("Disattivale qui in modo permanente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = notifEnabled,
                    onCheckedChange = {
                        notifEnabled = it
                        settings.lowSpaceNotificationsEnabled = it
                    }
                )
            }

            if (notifEnabled) {
                Spacer(Modifier.height(16.dp))
                val isSnoozed = snoozeUntil > System.currentTimeMillis()
                if (isSnoozed) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Posticipate fino al ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(snoozeUntil))}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { snoozeUntil = 0L; settings.lowSpaceNotificationSnoozeUntil = 0L }) {
                                Text("Riattiva ora")
                            }
                        }
                    }
                } else {
                    Text("Posticipa temporaneamente", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1 giorno" to 1L, "7 giorni" to 7L, "30 giorni" to 30L).forEach { (label, days) ->
                            OutlinedButton(onClick = {
                                val until = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days)
                                snoozeUntil = until
                                settings.lowSpaceNotificationSnoozeUntil = until
                            }) { Text(label) }
                        }
                    }
                }
            }

            SectionDivider()

            // --- Errori recenti (su tutte le sync) ---
            SectionTitle("Errori recenti")
            if (recentErrors.isEmpty()) {
                Text(
                    "Nessun errore registrato di recente su nessuna sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recentErrors.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "${profileNamesById[entry.profileId] ?: "Sync eliminata"} · " +
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(entry.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider()
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(20.dp))
    Divider()
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    pInfo.versionName ?: "?"
}.getOrDefault("?")
