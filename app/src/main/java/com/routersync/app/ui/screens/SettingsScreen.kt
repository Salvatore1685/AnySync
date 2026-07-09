package com.routersync.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.routersync.app.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var thresholdInput by remember { mutableStateOf(settings.storageWarningThresholdGb.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Spazio HDD", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sotto ogni sync su protocollo SMB viene mostrato lo spazio libero rimasto sull'HDD. " +
                    "Scegli sotto quale soglia mostrarlo in evidenza come avviso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = thresholdInput,
                onValueChange = { value ->
                    thresholdInput = value
                    value.toIntOrNull()?.let { if (it > 0) settings.storageWarningThresholdGb = it }
                },
                label = { Text("Soglia di avviso (GB)") },
                singleLine = true,
                modifier = Modifier.width(200.dp)
            )
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
