package com.routersync.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.routersync.app.data.AppSettings
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var notifEnabled by remember { mutableStateOf(settings.lowSpaceNotificationsEnabled) }
    var snoozeUntil by remember { mutableStateOf(settings.lowSpaceNotificationSnoozeUntil) }

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
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Notifiche spazio HDD in esaurimento", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
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

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Text(
                "Altre impostazioni (filtri predefiniti, gestione avanzata) arriveranno qui in futuro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
