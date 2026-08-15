package com.routersync.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.routersync.app.data.SyncProfile
import com.routersync.app.remote.RemoteBrowserClientFactory
import com.routersync.app.ui.SyncViewModel

/**
 * Schermata di consultazione dell'HDD per un profilo di sincronizzazione già salvato.
 *
 * Se [restrictToSyncFolder] è true (comportamento normale, accessibile dal pulsante
 * "Sfoglia file sincronizzati"), la navigazione è vincolata alla sola cartella di
 * destinazione scelta durante la configurazione: non è possibile risalire più in alto,
 * per evitare di visualizzare o cancellare per errore file estranei al backup.
 *
 * Se [restrictToSyncFolder] è false (accesso sbloccato con password da "Accesso avanzato
 * HDD"), la navigazione è libera su tutto l'HDD/condivisione.
 */
@Composable
fun HddGalleryScreen(
    profileId: Long,
    restrictToSyncFolder: Boolean = true,
    onClose: () -> Unit,
    viewModel: SyncViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val profile: SyncProfile? = profiles.find { it.id == profileId }

    if (profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val client = remember(profile.id) {
        RemoteBrowserClientFactory.create(profile.protocol, profile.host, profile.port, profile.username, profile.password)
    }

    val startPath = if (restrictToSyncFolder) profile.remoteBasePath else ""
    val boundary = if (restrictToSyncFolder) profile.remoteBasePath else ""

    RemoteBrowserContent(
        client = client,
        initialPath = startPath,
        rootBoundaryPath = boundary,
        mode = BrowserMode.VIEW_GALLERY,
        title = if (restrictToSyncFolder) profile.name else "HDD completo (${profile.host})",
        onClose = onClose
    )
}
