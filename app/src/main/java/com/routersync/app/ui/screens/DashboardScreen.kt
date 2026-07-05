package com.routersync.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddProfile: () -> Unit,
    onBrowseProfile: (Long) -> Unit,
    onAdminBrowse: (Long) -> Unit,
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
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onSyncNow = { viewModel.runManualSync(profile) },
                        onCancelSync = { viewModel.cancelSync(profile) },
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

@Composable
private fun ProfileCard(
    profile: SyncProfile,
    onSyncNow: () -> Unit,
    onCancelSync: () -> Unit,
    onDelete: () -> Unit,
    onBrowse: () -> Unit,
    onFreeSpace: () -> Unit,
    freeingSpace: Boolean
) {
    val context = LocalContext.current
    var showFreeSpaceConfirm by remember { mutableStateOf(false) }
    val workManager = remember { WorkManager.getInstance(context) }
    val workInfos by workManager
        .getWorkInfosForUniqueWorkFlow("sync_profile_${profile.id}_manual")
        .collectAsState(initial = emptyList())
    val isSyncing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

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
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(label = scheduleLabel(profile.scheduleType))
                Spacer(Modifier.width(6.dp))
                InfoChip(label = directionLabel(profile.direction))
                if (profile.autoFreeSpaceAfterSync) {
                    Spacer(Modifier.width(6.dp))
                    InfoChip(label = "Auto-libera spazio")
                }
            }

            Spacer(Modifier.height(8.dp))
            val lastSync = profile.lastSyncTimestamp?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "mai eseguita"

            AnimatedVisibility(visible = isSyncing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f).height(3.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sincronizzazione in corso… (tocca lo stop per interrompere)", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text("Ultima sync: $lastSync", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            profile.lastSyncStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (it.startsWith("OK")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sfoglia file sincronizzati")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showFreeSpaceConfirm = true },
                enabled = !freeingSpace,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (freeingSpace) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (freeingSpace) "Liberazione in corso…" else "Libera memoria dal telefono")
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

@Composable
private fun InfoChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
