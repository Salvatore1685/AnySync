package com.routersync.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.routersync.app.data.RemoteProtocol
import com.routersync.app.data.NetworkPreference
import com.routersync.app.data.ScheduleType
import com.routersync.app.data.SyncDirection
import com.routersync.app.data.SyncProfile
import com.routersync.app.discovery.DiscoveredDevice
import com.routersync.app.discovery.NetworkDiscovery
import com.routersync.app.remote.RemoteBrowserClientFactory
import com.routersync.app.remote.RemoteClientFactory
import com.routersync.app.ui.SyncViewModel
import kotlinx.coroutines.launch

/**
 * Wizard a step:
 * 1) Scelta cartella locale da sincronizzare (permesso SAF)
 * 2) Discovery automatica del router/HDD sulla rete + scelta protocollo/credenziali
 * 3) Cartella di destinazione sull'HDD (con possibilità di crearla)
 * 4) Piano di sincronizzazione e direzione
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileWizardScreen(onDone: () -> Unit, viewModel: SyncViewModel = viewModel()) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    // --- Stato raccolto lungo il wizard ---
    var localUri by remember { mutableStateOf<Uri?>(null) }
    var localDisplayName by remember { mutableStateOf("") }
    var syncName by remember { mutableStateOf("") }

    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var protocol by remember { mutableStateOf(RemoteProtocol.SMB) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(445) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var remoteFolderName by remember { mutableStateOf("Backup_Telefono") }
    var remoteBaseShare by remember { mutableStateOf("") } // es. nome share SMB

    var scheduleType by remember { mutableStateOf(ScheduleType.DAILY) }
    var direction by remember { mutableStateOf(SyncDirection.UPLOAD_ONLY) }
    var autoFreeSpace by remember { mutableStateOf(false) }
    var mirrorDeletes by remember { mutableStateOf(false) }
    var scheduledHour by remember { mutableStateOf(2) }
    var scheduledMinute by remember { mutableStateOf(0) }
    var scheduledDayOfWeek by remember { mutableStateOf(java.util.Calendar.MONDAY) }
    var scheduledDayOfMonth by remember { mutableStateOf(1) }
    var networkPreference by remember { mutableStateOf(NetworkPreference.ANY) }
    var requiresCharging by remember { mutableStateOf(false) }
    var homeWifiSsid by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            localUri = uri
            localDisplayName = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Cartella scelta"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Nuova sincronizzazione (${step + 1}/4)") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> StepLocalFolder(
                        displayName = localDisplayName,
                        syncName = syncName,
                        onSyncNameChange = { syncName = it },
                        onPick = { folderPicker.launch(null) }
                    )
                    1 -> StepRemoteDevice(
                        selectedDevice = selectedDevice,
                        protocol = protocol, host = host, port = port, username = username, password = password,
                        onDeviceSelected = { d ->
                            selectedDevice = d
                            host = d.ip
                            protocol = d.suggestedProtocol
                            port = d.openPort
                        },
                        onProtocolChange = { protocol = it },
                        onHostChange = { host = it },
                        onPortChange = { port = it },
                        onUserChange = { username = it },
                        onPassChange = { password = it }
                    )
                    2 -> StepRemoteFolder(
                        protocol = protocol, host = host, port = port, username = username, password = password,
                        shareName = remoteBaseShare, onShareChange = { remoteBaseShare = it },
                        folderName = remoteFolderName, onFolderChange = { remoteFolderName = it },
                        syncName = syncName
                    )
                    3 -> StepSchedule(
                        scheduleType = scheduleType, onScheduleChange = { scheduleType = it },
                        direction = direction, onDirectionChange = { direction = it },
                        autoFreeSpace = autoFreeSpace, onAutoFreeSpaceChange = { autoFreeSpace = it },
                        mirrorDeletes = mirrorDeletes, onMirrorDeletesChange = { mirrorDeletes = it },
                        scheduledHour = scheduledHour, scheduledMinute = scheduledMinute,
                        onTimeChange = { h, m -> scheduledHour = h; scheduledMinute = m },
                        scheduledDayOfWeek = scheduledDayOfWeek, onDayOfWeekChange = { scheduledDayOfWeek = it },
                        scheduledDayOfMonth = scheduledDayOfMonth, onDayOfMonthChange = { scheduledDayOfMonth = it },
                        networkPreference = networkPreference, onNetworkPreferenceChange = { networkPreference = it },
                        requiresCharging = requiresCharging, onRequiresChargingChange = { requiresCharging = it },
                        homeWifiSsid = homeWifiSsid, onHomeWifiSsidChange = { homeWifiSsid = it }
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) { Text("Indietro") }
                } else Spacer(Modifier)

                val canProceed = when (step) {
                    0 -> localUri != null && syncName.isNotBlank()
                    1 -> host.isNotBlank()
                    2 -> remoteFolderName.isNotBlank() && (protocol != RemoteProtocol.SMB || remoteBaseShare.isNotBlank())
                    else -> true
                }

                Button(
                    enabled = canProceed,
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            val remoteBasePath = if (protocol == RemoteProtocol.SMB)
                                "$remoteBaseShare/$remoteFolderName" else remoteFolderName
                            viewModel.saveProfile(
                                SyncProfile(
                                    name = syncName,
                                    protocol = protocol, host = host, port = port,
                                    username = username, password = password,
                                    remoteBasePath = remoteBasePath,
                                    localFolderUri = localUri.toString(),
                                    localFolderDisplayName = localDisplayName,
                                    scheduleType = scheduleType, direction = direction,
                                    autoFreeSpaceAfterSync = autoFreeSpace,
                                    mirrorDeletes = mirrorDeletes && direction == SyncDirection.BIDIRECTIONAL,
                                    scheduledHour = scheduledHour, scheduledMinute = scheduledMinute,
                                    scheduledDayOfWeek = scheduledDayOfWeek, scheduledDayOfMonth = scheduledDayOfMonth,
                                    networkPreference = networkPreference, requiresCharging = requiresCharging,
                                    homeWifiSsid = homeWifiSsid
                                )
                            )
                            onDone()
                        }
                    }
                ) { Text(if (step < 3) "Avanti" else "Crea sincronizzazione") }
            }
        }
    }
}

@Composable
private fun StepLocalFolder(
    displayName: String,
    syncName: String,
    onSyncNameChange: (String) -> Unit,
    onPick: () -> Unit
) {
    Text("1. Nome e cartella da sincronizzare", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = syncName,
        onValueChange = onSyncNameChange,
        label = { Text("Nome di questa sincronizzazione") },
        supportingText = { Text("Sarà anche il nome della cartella creata sull'HDD, per tenere tutto organizzato") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(20.dp))
    Text("Scegli la cartella sul telefono da tenere sincronizzata con l'HDD del router. L'app chiederà il permesso di accesso solo a questa cartella.")
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onPick) { Text(if (displayName.isBlank()) "Scegli cartella…" else "Cartella scelta: $displayName") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepRemoteDevice(
    selectedDevice: DiscoveredDevice?,
    protocol: RemoteProtocol, host: String, port: Int, username: String, password: String,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onProtocolChange: (RemoteProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }

    Text("2. Router / HDD di rete", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        devices = emptyList()
        scanning = true
        scope.launch {
            NetworkDiscovery(context).scan().collect { d ->
                devices = devices + d
            }
            scanning = false
        }
    }) { Text(if (scanning) "Scansione in corso…" else "Rileva automaticamente dispositivi") }

    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.heightIn(max = 160.dp)) {
        items(devices) { d ->
            ListItem(
                headlineContent = { Text(d.hostname ?: d.ip) },
                supportingContent = { Text("${d.suggestedProtocol} rilevato su ${d.ip}:${d.openPort}") },
                trailingContent = {
                    if (selectedDevice == d) Icon(Icons.Filled.Sync, contentDescription = null)
                },
                modifier = Modifier.clickableSelect { onDeviceSelected(d) }
            )
            Divider()
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Oppure inserisci manualmente:", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))

    ProtocolSelector(protocol, onProtocolChange)
    OutlinedTextField(value = host, onValueChange = onHostChange, label = { Text("Indirizzo IP / host") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = port.toString(), onValueChange = { it.toIntOrNull()?.let(onPortChange) }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = username, onValueChange = onUserChange, label = { Text("Utente (opzionale)") }, supportingText = { Text("Lascia vuoto se il router non richiede autenticazione") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = password, onValueChange = onPassChange, label = { Text("Password (opzionale)") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ProtocolSelector(protocol: RemoteProtocol, onChange: (RemoteProtocol) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteProtocol.entries.forEach { p ->
            FilterChip(selected = protocol == p, onClick = { onChange(p) }, label = { Text(p.name) })
        }
    }
}

@Composable
private fun StepRemoteFolder(
    protocol: RemoteProtocol, host: String, port: Int, username: String, password: String,
    shareName: String, onShareChange: (String) -> Unit,
    folderName: String, onFolderChange: (String) -> Unit,
    syncName: String
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }

    Text("3. Cartella di destinazione sull'HDD", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))

    OutlinedButton(onClick = { showBrowser = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Sfoglia HDD e scegli/crea cartella")
    }
    Spacer(Modifier.height(16.dp))
    Text("Oppure inserisci manualmente:", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))

    if (protocol == RemoteProtocol.SMB) {
        OutlinedTextField(value = shareName, onValueChange = onShareChange, label = { Text("Nome share SMB (es. HDD)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
    OutlinedTextField(value = folderName, onValueChange = onFolderChange, label = { Text("Nome cartella da creare/usare") }, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(16.dp))
    Button(enabled = !creating, onClick = {
        creating = true
        status = null
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fakeProfile = SyncProfile(
                    name = "", protocol = protocol, host = host, port = port,
                    username = username, password = password,
                    remoteBasePath = if (protocol == RemoteProtocol.SMB) "$shareName/$folderName" else folderName,
                    localFolderUri = "", localFolderDisplayName = "",
                    scheduleType = ScheduleType.MANUAL, direction = SyncDirection.UPLOAD_ONLY
                )
                val client = RemoteClientFactory.create(fakeProfile)
                client.connect()
                val relative = folderName
                client.mkdirs(relative)
                // Crea la cartella dati con il nome scelto per questa sincronizzazione
                val dataFolder = com.routersync.app.sync.sanitizeFolderName(syncName)
                client.mkdirs("$relative/$dataFolder")
                client.disconnect()
                status = "Cartella pronta su HDD, con sottocartella \"$dataFolder\" ✔"
            } catch (e: Exception) {
                status = "Errore: ${e.message}"
            } finally {
                creating = false
            }
        }
    }) { Text(if (creating) "Creazione…" else "Crea/Verifica cartella sull'HDD") }

    status?.let { Spacer(Modifier.height(8.dp)); Text(it) }

    if (showBrowser) {
        Dialog(onDismissRequest = { showBrowser = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val client = remember { RemoteBrowserClientFactory.create(protocol, host, port, username, password) }
            RemoteBrowserContent(
                client = client,
                mode = BrowserMode.PICK_FOLDER,
                title = "Scegli cartella di destinazione",
                onPickPath = { picked ->
                    if (protocol == RemoteProtocol.SMB) {
                        // Per SMB il primo segmento del path scelto è il nome della share
                        val share = picked.trim('/').substringBefore("/")
                        val rest = picked.trim('/').substringAfter("/", "")
                        onShareChange(share)
                        onFolderChange(rest)
                    } else {
                        onFolderChange(picked.trim('/'))
                    }
                    showBrowser = false
                },
                onClose = { showBrowser = false }
            )
        }
    }
}

@Composable
private fun StepSchedule(
    scheduleType: ScheduleType, onScheduleChange: (ScheduleType) -> Unit,
    direction: SyncDirection, onDirectionChange: (SyncDirection) -> Unit,
    autoFreeSpace: Boolean, onAutoFreeSpaceChange: (Boolean) -> Unit,
    mirrorDeletes: Boolean, onMirrorDeletesChange: (Boolean) -> Unit,
    scheduledHour: Int, scheduledMinute: Int, onTimeChange: (Int, Int) -> Unit,
    scheduledDayOfWeek: Int, onDayOfWeekChange: (Int) -> Unit,
    scheduledDayOfMonth: Int, onDayOfMonthChange: (Int) -> Unit,
    networkPreference: NetworkPreference,
    onNetworkPreferenceChange: (NetworkPreference) -> Unit,
    requiresCharging: Boolean, onRequiresChargingChange: (Boolean) -> Unit,
    homeWifiSsid: String?, onHomeWifiSsidChange: (String?) -> Unit
) {
    val context = LocalContext.current

    Text("4. Piano di sincronizzazione", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text("Frequenza", style = MaterialTheme.typography.labelLarge)
    Column {
        ScheduleType.entries.forEach { s ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = scheduleType == s, onClick = { onScheduleChange(s) })
                Text(scheduleLabelIt(s))
            }
        }
    }

    // Orario preciso: rilevante per tutte le frequenze a tempo tranne Manuale
    if (scheduleType != ScheduleType.MANUAL && scheduleType != ScheduleType.HOURLY) {
        Spacer(Modifier.height(12.dp))
        Text("Orario", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = {
            android.app.TimePickerDialog(
                context,
                { _, h, m -> onTimeChange(h, m) },
                scheduledHour, scheduledMinute, true
            ).show()
        }) {
            Text("%02d:%02d".format(scheduledHour, scheduledMinute))
        }
    }

    // Sync oraria: si può scegliere solo il minuto in cui scatta ogni ora (es. "sempre al minuto 15")
    if (scheduleType == ScheduleType.HOURLY) {
        Spacer(Modifier.height(12.dp))
        Text("Minuto di ogni ora", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = scheduledMinute.toString(),
            onValueChange = { it.toIntOrNull()?.let { m -> if (m in 0..59) onTimeChange(scheduledHour, m) } },
            label = { Text("Minuto (0-59)") },
            supportingText = { Text("Es. 15 = parte sempre a XX:15, ogni ora") },
            modifier = Modifier.width(160.dp)
        )
    }

    // Giorno della settimana: solo per Settimanale
    if (scheduleType == ScheduleType.WEEKLY) {
        Spacer(Modifier.height(12.dp))
        Text("Giorno della settimana", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekDaysIt().forEach { (calendarDay, label) ->
                FilterChip(
                    selected = scheduledDayOfWeek == calendarDay,
                    onClick = { onDayOfWeekChange(calendarDay) },
                    label = { Text(label) }
                )
            }
        }
    }

    // Giorno del mese: solo per Mensile
    if (scheduleType == ScheduleType.MONTHLY) {
        Spacer(Modifier.height(12.dp))
        Text("Giorno del mese", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = scheduledDayOfMonth.toString(),
            onValueChange = { it.toIntOrNull()?.let { d -> if (d in 1..31) onDayOfMonthChange(d) } },
            label = { Text("Giorno (1-31)") },
            supportingText = { Text("Nei mesi più corti verrà usato l'ultimo giorno disponibile") },
            modifier = Modifier.width(160.dp)
        )
    }

    Spacer(Modifier.height(16.dp))
    Divider()
    Spacer(Modifier.height(12.dp))
    Text("Direzione", style = MaterialTheme.typography.labelLarge)
    Column {
        SyncDirection.entries.forEach { d ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = direction == d, onClick = { onDirectionChange(d) })
                Text(directionLabelIt(d))
            }
        }
    }

    // Condizioni di avvio: valgono anche per la sync manuale (che rispetterà queste condizioni quando premuta)
    run {
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Text("Condizioni di avvio", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Column {
            networkPreferenceOptionsIt().forEach { (pref, label, desc) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    RadioButton(selected = networkPreference == pref, onClick = { onNetworkPreferenceChange(pref) })
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(label)
                        Text(desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Per le opzioni che coinvolgono il Wi-Fi di casa, permette di rilevarlo ora
        if (networkPreference == NetworkPreference.HOME_WIFI_ONLY ||
            networkPreference == NetworkPreference.HOME_WIFI_OR_MOBILE
        ) {
            Spacer(Modifier.height(8.dp))
            val detectedSsid = remember { com.routersync.app.work.NetworkConditionChecker.currentWifiSsid(context) }
            if (homeWifiSsid != null) {
                Text("Wi-Fi di casa: $homeWifiSsid", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                TextButton(onClick = { onHomeWifiSsidChange(null) }) { Text("Cambia") }
            } else if (detectedSsid != null) {
                OutlinedButton(onClick = { onHomeWifiSsidChange(detectedSsid) }) {
                    Text("Usa \"$detectedSsid\" come Wi-Fi di casa")
                }
            } else {
                Text(
                    "Per rilevare il Wi-Fi di casa, apri questo wizard mentre sei connesso alla rete Wi-Fi di casa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (networkPreference == NetworkPreference.MOBILE_ONLY ||
            networkPreference == NetworkPreference.HOME_WIFI_OR_MOBILE
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    "Questa opzione richiede un indirizzo IP pubblico sulla tua linea internet di casa. Se il tuo router è dietro CGNAT (comune con alcuni operatori), la sincronizzazione tramite dati mobili non riuscirà a connettersi finché non risolvi questo aspetto con il tuo operatore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Solo se in carica", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = requiresCharging, onCheckedChange = onRequiresChargingChange)
        }
    }

    // Liberazione spazio automatica: ha senso solo quando si carica qualcosa dal telefono verso l'HDD
    if (direction == SyncDirection.UPLOAD_ONLY || direction == SyncDirection.BIDIRECTIONAL) {
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("Libera spazio automaticamente", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Elimina ogni file dal telefono subito dopo averlo caricato sull'HDD",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = autoFreeSpace, onCheckedChange = onAutoFreeSpaceChange)
        }
    }

    // Propagazione cancellazioni: ha senso solo per la sincronizzazione bidirezionale
    if (direction == SyncDirection.BIDIRECTIONAL) {
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Text("Se un file viene cancellato...", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Column {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = !mirrorDeletes, onClick = { onMirrorDeletesChange(false) })
                Column {
                    Text("Non cancellare mai, solo aggiungi (predefinito)")
                    Text(
                        "I file cancellati da un lato restano intatti dall'altro",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = mirrorDeletes, onClick = { onMirrorDeletesChange(true) })
                Column {
                    Text("Cancella su entrambi i lati (mirror completo)")
                    Text(
                        "Se cancelli un file dal telefono o dall'HDD, verrà cancellato anche dall'altra parte al prossimo aggiornamento",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** Elenco dei giorni della settimana con le costanti di java.util.Calendar corrispondenti. */
private fun weekDaysIt(): List<Pair<Int, String>> = listOf(
    java.util.Calendar.MONDAY to "Lun",
    java.util.Calendar.TUESDAY to "Mar",
    java.util.Calendar.WEDNESDAY to "Mer",
    java.util.Calendar.THURSDAY to "Gio",
    java.util.Calendar.FRIDAY to "Ven",
    java.util.Calendar.SATURDAY to "Sab",
    java.util.Calendar.SUNDAY to "Dom"
)

private fun networkPreferenceOptionsIt(): List<Triple<NetworkPreference, String, String>> {
    return listOf(
        Triple(NetworkPreference.ANY, "Qualsiasi rete", "Wi-Fi o dati mobili, quello disponibile al momento"),
        Triple(NetworkPreference.WIFI_ONLY, "Solo Wi-Fi", "Qualsiasi rete Wi-Fi, non necessariamente quella di casa"),
        Triple(NetworkPreference.HOME_WIFI_ONLY, "Solo Wi-Fi di casa", "Aspetta finché non sei connesso alla tua rete di casa"),
        Triple(NetworkPreference.MOBILE_ONLY, "Solo dati mobili", "Richiede un IP pubblico sulla linea di casa"),
        Triple(NetworkPreference.HOME_WIFI_OR_MOBILE, "Wi-Fi di casa o dati mobili", "La prima disponibile tra le due")
    )
}

private fun scheduleLabelIt(s: ScheduleType) = when (s) {
    ScheduleType.MANUAL -> "Manuale (tasto dedicato)"
    ScheduleType.HOURLY -> "Ogni ora"
    ScheduleType.DAILY -> "Giornaliero"
    ScheduleType.WEEKLY -> "Settimanale"
    ScheduleType.MONTHLY -> "Mensile"
}

private fun directionLabelIt(d: SyncDirection) = when (d) {
    SyncDirection.UPLOAD_ONLY -> "Telefono → HDD"
    SyncDirection.DOWNLOAD_ONLY -> "HDD → Telefono"
    SyncDirection.BIDIRECTIONAL -> "Bidirezionale"
}

// Piccola estensione per rendere cliccabile un ListItem senza tirare in ballo Modifier.clickable ovunque
private fun Modifier.clickableSelect(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
