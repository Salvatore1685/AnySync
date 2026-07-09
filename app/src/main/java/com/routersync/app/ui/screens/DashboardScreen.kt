package com.routersync.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.routersync.app.ADMIN_UNLOCK_PASSWORD
import com.routersync.app.data.RemoteProtocol
import com.routersync.app.data.SyncDirection
import com.routersync.app.data.SyncProfile
import com.routersync.app.data.ScheduleType
import com.routersync.app.ui.SyncViewModel
import com.routersync.app.ui.theme.ErrorDark
import com.routersync.app.ui.theme.ErrorLight
import com.routersync.app.ui.theme.SuccessDark
import com.routersync.app.ui.theme.SuccessLight
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colore di successo coerente con il tema chiaro/scuro (i token semantici non fanno parte dello schema Material di default). */
@Composable
private fun successColor() = if (isSystemInDarkTheme()) SuccessDark else SuccessLight

@Composable
private fun errorSemanticColor() = if (isSystemInDarkTheme()) ErrorDark else ErrorLight

/** Bottone con una leggera "pressione" al tocco (scala + opacità), come nei componenti SyncDrive. */
@Composable
private fun PressableScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    Box(
        modifier
            .scale(scale)
            .clickableNoRipple(interactionSource, onClick)
    ) { content() }
}

@Composable
private fun Modifier.clickableNoRipple(interactionSource: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onBrowseProfile: (Long) -> Unit,
    onAdminBrowse: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SyncViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val freeingSpaceProfileId by viewModel.freeingSpaceProfileId.collectAsState()
    val freeSpaceResult by viewModel.freeSpaceResult.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAdminPasswordDialog by remember { mutableStateOf(false) }
    var adminPasswordError by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(freeSpaceResult) {
        freeSpaceResult?.let { result ->
            snackbarHostState.showSnackbar(result.message)
            viewModel.clearFreeSpaceResult()
        }
    }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("EverySync", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Altre opzioni")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Accesso avanzato HDD") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                if (profiles.isEmpty()) {
                                    android.widget.Toast.makeText(context, "Crea prima almeno una sincronizzazione", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    showAdminPasswordDialog = true
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Impostazioni") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProfile,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuova sync") }
            )
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Router,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nessuna sincronizzazione configurata",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tocca \"Nuova sync\" per collegare il tuo primo HDD",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { StatsSummaryCard(profiles) }
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onSyncNow = { viewModel.runManualSync(profile) },
                        onCancelSync = { viewModel.cancelSync(profile) },
                        onEdit = { onEditProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onBrowse = { onBrowseProfile(profile.id) },
                        onFreeSpace = { viewModel.freeLocalSpace(profile) },
                        freeingSpace = freeingSpaceProfileId == profile.id
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAdminPasswordDialog) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdminPasswordDialog = false; adminPasswordError = false },
            title = { Text("Accesso avanzato HDD") },
            text = {
                Column {
                    Text("Questa modalità permette di navigare liberamente su tutto l'HDD, oltre la cartella di sincronizzazione. Inserisci la password per continuare.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; adminPasswordError = false },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = adminPasswordError,
                        singleLine = true
                    )
                    if (adminPasswordError) {
                        Text("Password errata", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passwordInput == ADMIN_UNLOCK_PASSWORD) {
                        showAdminPasswordDialog = false
                        adminPasswordError = false
                        onAdminBrowse(profiles.first().id)
                    } else {
                        adminPasswordError = true
                    }
                }) { Text("Sblocca") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPasswordDialog = false; adminPasswordError = false }) { Text("Annulla") }
            }
        )
    }
}

/** Riga di statistiche in evidenza in cima alla dashboard, come nella home di SyncDrive. */
@Composable
private fun StatsSummaryCard(profiles: List<SyncProfile>) {
    val activeCount = profiles.count { it.scheduleType != ScheduleType.MANUAL }
    val context = LocalContext.current
    val smbProfile = remember(profiles) { profiles.firstOrNull { it.protocol == RemoteProtocol.SMB } }

    var freeSpaceGb by remember(smbProfile?.id) { mutableStateOf<Double?>(null) }
    LaunchedEffect(smbProfile?.id) {
        val p = smbProfile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val client = com.routersync.app.remote.RemoteClientFactory.create(p)
            runCatching {
                client.connect()
                client.freeSpaceBytes()
            }.getOrNull()?.let { bytes -> freeSpaceGb = bytes / 1_073_741_824.0 }
            runCatching { client.disconnect() }
        }
    }
    val settings = remember { com.routersync.app.data.AppSettings(context) }
    val lowSpace = freeSpaceGb != null && freeSpaceGb!! < settings.storageWarningThresholdGb
    val success = successColor()
    val errorColor = errorSemanticColor()

    ElevatedCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(value = profiles.size.toString(), label = "Sync totali", modifier = Modifier.weight(1f))
            StatDivider()
            StatItem(value = activeCount.toString(), label = "Pianificate", modifier = Modifier.weight(1f))
            StatDivider()
            when {
                smbProfile == null -> StatItem(value = "—", label = "Spazio HDD", modifier = Modifier.weight(1f))
                freeSpaceGb == null -> StatItem(value = "…", label = "Spazio HDD", modifier = Modifier.weight(1f))
                else -> StatItem(
                    value = "%.0f GB".format(freeSpaceGb),
                    label = "Liberi su HDD",
                    modifier = Modifier.weight(1f),
                    valueColor = if (lowSpace) errorColor else success
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.primary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun ProfileCard(
    profile: SyncProfile,
    onSyncNow: () -> Unit,
    onCancelSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBrowse: () -> Unit,
    onFreeSpace: () -> Unit,
    freeingSpace: Boolean
) {
    val context = LocalContext.current
    var showFreeSpaceConfirm by remember { mutableStateOf(false) }
    val workManager = remember { WorkManager.getInstance(context) }
    val manualWorkInfos by workManager
        .getWorkInfosForUniqueWorkFlow("sync_profile_${profile.id}_manual")
        .collectAsState(initial = emptyList())
    val scheduledWorkInfos by workManager
        .getWorkInfosForUniqueWorkFlow("sync_profile_${profile.id}")
        .collectAsState(initial = emptyList())
    // Solo RUNNING conta come "in corso": ENQUEUED per una sync pianificata può restare tale
    // per ore (in attesa del prossimo orario), e non deve mostrare l'animazione di caricamento.
    val isSyncing = (manualWorkInfos + scheduledWorkInfos).any { it.state == WorkInfo.State.RUNNING }
    val success = successColor()
    val errorColor = errorSemanticColor()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProtocolAvatar(profile.protocol)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${profile.host} → ${profile.remoteBasePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { if (isSyncing) onCancelSync() else onSyncNow() }) {
                    if (isSyncing) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                            Icon(Icons.Default.Stop, contentDescription = "Ferma sincronizzazione", modifier = Modifier.size(14.dp))
                        }
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizza ora")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                InfoChip(label = scheduleLabel(profile.scheduleType), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                InfoChip(label = directionLabel(profile.direction), color = MaterialTheme.colorScheme.primary)
                scheduleTimeLabel(profile)?.let { timeLabel ->
                    Spacer(Modifier.width(6.dp))
                    InfoChip(label = timeLabel, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.width(6.dp))
                InfoChip(label = networkPreferenceLabel(profile.networkPreference), color = MaterialTheme.colorScheme.secondary)
                if (profile.autoFreeSpaceAfterSync) {
                    Spacer(Modifier.width(6.dp))
                    InfoChip(label = "Auto-libera spazio", color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(10.dp))
            val lastSync = profile.lastSyncTimestamp?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "mai eseguita"

            AnimatedVisibility(visible = isSyncing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f).height(3.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sincronizzazione in corso… (tocca lo stop per interrompere)", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Pallino di stato + testo, come l'indicatore di connessione in SyncDrive
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = when {
                    profile.lastSyncStatus?.startsWith("OK") == true -> success
                    profile.lastSyncStatus?.startsWith("In attesa") == true -> MaterialTheme.colorScheme.tertiary
                    profile.lastSyncStatus != null -> errorColor
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(6.dp))
                Text("Ultima sync: $lastSync", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            profile.lastSyncStatus?.let {
                val statusColor = when {
                    it.startsWith("OK") -> success
                    it.startsWith("In attesa") -> MaterialTheme.colorScheme.tertiary
                    else -> errorColor
                }
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            PressableScale(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                OutlinedCardButton(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Sfoglia file sincronizzati"
                )
            }

            Spacer(Modifier.height(8.dp))
            PressableScale(
                onClick = { if (!freeingSpace) showFreeSpaceConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedCardButton(
                    icon = Icons.Default.CleaningServices,
                    label = if (freeingSpace) "Liberazione in corso…" else "Libera memoria dal telefono",
                    loading = freeingSpace
                )
            }
        }
    }

    if (showFreeSpaceConfirm) {
        AlertDialog(
            onDismissRequest = { showFreeSpaceConfirm = false },
            title = { Text("Liberare memoria?") },
            text = {
                Text("Verranno eliminati dal telefono tutti i file già presenti sull'HDD (che rimarranno lì al sicuro). I file non ancora sincronizzati non verranno toccati.")
            },
            confirmButton = {
                TextButton(onClick = { showFreeSpaceConfirm = false; onFreeSpace() }) { Text("Libera memoria") }
            },
            dismissButton = { TextButton(onClick = { showFreeSpaceConfirm = false }) { Text("Annulla") } }
        )
    }
}

/** Bottone in stile "outlined card": bordo sottile, angoli morbidi, icona + testo centrati. */
@Composable
private fun OutlinedCardButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, loading: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProtocolAvatar(protocol: RemoteProtocol) {
    val (icon, color) = when (protocol) {
        RemoteProtocol.SMB -> Icons.Default.Dns to Color(0xFF2962FF)
        RemoteProtocol.FTP -> Icons.Default.FolderOpen to Color(0xFFEF6C00)
        RemoteProtocol.WEBDAV -> Icons.Default.CloudSync to Color(0xFF00897B)
    }
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = protocol.name, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** Badge a pillola con sfondo colorato trasparente, come i badge di stato in SyncDrive. */
@Composable
private fun InfoChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun scheduleLabel(s: ScheduleType) = when (s) {
    ScheduleType.MANUAL -> "Manuale"
    ScheduleType.HOURLY -> "Ogni ora"
    ScheduleType.DAILY -> "Giornaliero"
    ScheduleType.WEEKLY -> "Settimanale"
    ScheduleType.MONTHLY -> "Mensile"
}

private fun directionLabel(d: SyncDirection) = when (d) {
    SyncDirection.UPLOAD_ONLY -> "Telefono → HDD"
    SyncDirection.DOWNLOAD_ONLY -> "HDD → Telefono"
    SyncDirection.BIDIRECTIONAL -> "Bidirezionale"
}

/** Etichetta sintetica di giorno/orario pianificato, es. "Lun 14:30" o "Ogni ora, :15". Null per Manuale. */
private fun scheduleTimeLabel(profile: SyncProfile): String? {
    val time = "%02d:%02d".format(profile.scheduledHour, profile.scheduledMinute)
    return when (profile.scheduleType) {
        ScheduleType.MANUAL -> null
        ScheduleType.HOURLY -> "Ogni ora, :%02d".format(profile.scheduledMinute)
        ScheduleType.DAILY -> time
        ScheduleType.WEEKLY -> "${weekDayShortLabel(profile.scheduledDayOfWeek)} $time"
        ScheduleType.MONTHLY -> "Il ${profile.scheduledDayOfMonth} del mese, $time"
    }
}

private fun weekDayShortLabel(calendarDay: Int) = when (calendarDay) {
    java.util.Calendar.MONDAY -> "Lun"
    java.util.Calendar.TUESDAY -> "Mar"
    java.util.Calendar.WEDNESDAY -> "Mer"
    java.util.Calendar.THURSDAY -> "Gio"
    java.util.Calendar.FRIDAY -> "Ven"
    java.util.Calendar.SATURDAY -> "Sab"
    java.util.Calendar.SUNDAY -> "Dom"
    else -> ""
}

/** Etichetta del tipo di connessione richiesto per l'avvio (non il nome della rete, solo la condizione). */
private fun networkPreferenceLabel(p: com.routersync.app.data.NetworkPreference) = when (p) {
    com.routersync.app.data.NetworkPreference.ANY -> "Qualsiasi rete"
    com.routersync.app.data.NetworkPreference.WIFI_ONLY -> "Solo Wi-Fi"
    com.routersync.app.data.NetworkPreference.HOME_WIFI_ONLY -> "Solo Wi-Fi di casa"
    com.routersync.app.data.NetworkPreference.MOBILE_ONLY -> "Solo dati mobili"
    com.routersync.app.data.NetworkPreference.HOME_WIFI_OR_MOBILE -> "Wi-Fi casa o dati"
}
