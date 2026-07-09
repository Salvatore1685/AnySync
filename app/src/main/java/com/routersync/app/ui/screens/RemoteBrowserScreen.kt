package com.routersync.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.documentfile.provider.DocumentFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.routersync.app.remote.RemoteClient
import com.routersync.app.remote.RemoteEntry
import com.routersync.app.remote.downloadBytes
import com.routersync.app.remote.isAudio
import com.routersync.app.remote.isImage
import com.routersync.app.remote.isVideo
import com.routersync.app.sync.FileCategory
import com.routersync.app.sync.categoryForFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Modalità del browser: scelta cartella (wizard) oppure semplice consultazione (galleria). */
enum class BrowserMode { PICK_FOLDER, VIEW_GALLERY }

/** Limite di sicurezza: oltre questa quantità di miniature in cache, la svuotiamo per non accumulare memoria all'infinito. */
private const val MAX_THUMBNAIL_CACHE = 300

/**
 * Contenuto del browser di rete, riutilizzabile sia dentro il wizard (in un Dialog a schermo
 * intero) sia come schermata a sé stante per consultare i file già sincronizzati.
 *
 * [rootBoundaryPath], se diverso da stringa vuota, impedisce di risalire con la freccia
 * "indietro" oltre quel path.
 *
 * In modalità galleria sono sempre disponibili i filtri rapidi per tipo di file (Foto/Video/
 * Audio/Documenti): selezionandone uno, l'app scansiona ricorsivamente la cartella in cui ti
 * trovi in quel momento, in qualunque punto della struttura.
 *
 * Le miniature vengono decodificate e ridimensionate immediatamente al download, e solo il
 * risultato (molto più leggero) resta in cache: i byte originali del file non vengono mai
 * conservati oltre la decodifica, per evitare di esaurire la memoria con gallerie numerose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserContent(
    client: RemoteClient,
    initialPath: String = "",
    rootBoundaryPath: String = "",
    mode: BrowserMode,
    title: String = "Sfoglia HDD",
    onPickPath: ((String) -> Unit)? = null,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var connected by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf(initialPath) }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var viewingImageIndex by remember { mutableStateOf<Int?>(null) }
    var openingFile by remember { mutableStateOf<RemoteEntry?>(null) }
    var sharingBusy by remember { mutableStateOf(false) }
    var downloadingBusy by remember { mutableStateOf(false) }
    var pendingDownloadSingle by remember { mutableStateOf<RemoteEntry?>(null) }
    var pendingDownloadMultiple by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }

    // Selettore di sistema "Salva con nome": per scaricare un singolo file dove preferisce l'utente
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val entry = pendingDownloadSingle
        pendingDownloadSingle = null
        if (uri != null && entry != null) {
            downloadingBusy = true
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> client.download(entry.path, out) }
                }
                downloadingBusy = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "\"${entry.name}\" salvato sul telefono", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Selettore di sistema "Scegli cartella": per scaricare più file insieme nella cartella scelta
    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        val entries = pendingDownloadMultiple.filter { !it.isDirectory }
        pendingDownloadMultiple = emptyList()
        if (treeUri != null && entries.isNotEmpty()) {
            downloadingBusy = true
            scope.launch(Dispatchers.IO) {
                val destDir = DocumentFile.fromTreeUri(context, treeUri)
                var saved = 0
                for (entry in entries) {
                    runCatching {
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                            entry.name.substringAfterLast('.', "").lowercase()
                        ) ?: "application/octet-stream"
                        val target = destDir?.createFile(mime, entry.name)
                        target?.let { doc ->
                            context.contentResolver.openOutputStream(doc.uri)?.use { out -> client.download(entry.path, out) }
                        }
                        saved++
                    }
                }
                downloadingBusy = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$saved file salvati sul telefono", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadSingle(entry: RemoteEntry) {
        pendingDownloadSingle = entry
        createDocumentLauncher.launch(entry.name)
    }

    fun downloadMultiple(targets: List<RemoteEntry>) {
        val files = targets.filter { !it.isDirectory }
        if (files.isEmpty()) {
            Toast.makeText(context, "Seleziona almeno un file (le cartelle non si possono scaricare così)", Toast.LENGTH_LONG).show()
            return
        }
        pendingDownloadMultiple = files
        openTreeLauncher.launch(null)
    }
    // Cache delle sole MINIATURE già decodificate e ridimensionate (leggere): niente byte grezzi qui.
    val thumbnailCache = remember { mutableStateMapOf<String, Bitmap?>() }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { mutableStateListOf<String>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    var activeFilter by remember { mutableStateOf<FileCategory?>(null) }
    var filteredEntries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var filterLoading by remember { mutableStateOf(false) }

    val atBoundaryRoot = currentPath == rootBoundaryPath
    val showFilterBar = mode == BrowserMode.VIEW_GALLERY

    var reloadTrigger by remember { mutableStateOf(0) }

    fun reloadCurrentFolder() {
        reloadTrigger++
    }

    fun loadThumbnail(entry: RemoteEntry) {
        if (thumbnailCache.containsKey(entry.path)) return
        scope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                val bytes = client.downloadBytes(entry.path)
                decodeSampledBitmap(bytes, 150) // decodifica subito ridotta: i byte grezzi non vengono conservati
            }.getOrNull()
            if (thumbnailCache.size > MAX_THUMBNAIL_CACHE) thumbnailCache.clear()
            thumbnailCache[entry.path] = bitmap
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                client.connect()
                connected = true
            } catch (e: Exception) {
                connectionError = "Impossibile connettersi: ${e.message}"
            }
        }
    }

    LaunchedEffect(currentPath, connected, reloadTrigger) {
        if (!connected) return@LaunchedEffect
        loading = true
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val result = client.listFiles(currentPath)
                    .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                entries = result
            } catch (e: Exception) {
                loadError = "Errore nel caricamento: ${e.message}"
                entries = emptyList()
            }
        }
        loading = false
    }

    LaunchedEffect(activeFilter, currentPath) {
        val filter = activeFilter
        if (filter == null) return@LaunchedEffect
        filterLoading = true
        withContext(Dispatchers.IO) {
            filteredEntries = runCatching { scanForCategory(client, currentPath, filter) }.getOrDefault(emptyList())
        }
        filterLoading = false
    }

    val displayedEntries = if (activeFilter != null) filteredEntries else entries
    val imageEntries = remember(displayedEntries) { displayedEntries.filter { it.isImage() } }

    fun openExternally(entry: RemoteEntry) {
        openingFile = entry
        scope.launch {
            openFileExternally(context, client, entry)
            openingFile = null
        }
    }

    fun toggleSelection(entry: RemoteEntry) {
        if (selectedPaths.contains(entry.path)) selectedPaths.remove(entry.path) else selectedPaths.add(entry.path)
    }

    fun handleTap(entry: RemoteEntry) {
        if (selectionMode) {
            toggleSelection(entry)
        } else {
            when {
                entry.isDirectory -> currentPath = entry.path
                entry.isImage() -> viewingImageIndex = imageEntries.indexOfFirst { it.path == entry.path }
                else -> openExternally(entry)
            }
        }
    }

    fun handleLongPress(entry: RemoteEntry) {
        if (mode != BrowserMode.VIEW_GALLERY) return
        if (!selectionMode) selectionMode = true
        toggleSelection(entry)
    }

    fun performDelete() {
        val toDelete = displayedEntries.filter { selectedPaths.contains(it.path) }
        deleting = true
        scope.launch(Dispatchers.IO) {
            for (entry in toDelete) {
                runCatching { deleteRecursively(client, entry) }
            }
            withContext(Dispatchers.Main) {
                selectedPaths.clear()
                selectionMode = false
                deleting = false
                activeFilter = null
                reloadCurrentFolder()
            }
        }
    }

    fun performShare(targets: List<RemoteEntry>) {
        val files = targets.filter { !it.isDirectory }
        if (files.isEmpty()) {
            Toast.makeText(context, "Seleziona almeno un file (le cartelle non si possono condividere)", Toast.LENGTH_LONG).show()
            return
        }
        sharingBusy = true
        scope.launch {
            shareFilesExternally(context, client, files)
            sharingBusy = false
            selectionMode = false
            selectedPaths.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selectionMode -> "${selectedPaths.size} selezionati"
                            activeFilter != null -> filterLabel(activeFilter!!)
                            atBoundaryRoot -> title
                            else -> currentPath
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectionMode -> { selectionMode = false; selectedPaths.clear() }
                            activeFilter != null -> activeFilter = null
                            atBoundaryRoot -> onClose()
                            currentPath.isBlank() -> onClose()
                            else -> currentPath = currentPath.trim('/').substringBeforeLast("/", "")
                        }
                    }) {
                        Icon(
                            if (selectionMode || activeFilter != null || atBoundaryRoot) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { downloadMultiple(displayedEntries.filter { selectedPaths.contains(it.path) }) }, enabled = selectedPaths.isNotEmpty()) {
                            Icon(Icons.Default.Download, contentDescription = "Scarica sul telefono")
                        }
                        IconButton(onClick = { performShare(displayedEntries.filter { selectedPaths.contains(it.path) }) }, enabled = selectedPaths.isNotEmpty()) {
                            Icon(Icons.Default.Share, contentDescription = "Condividi selezionati")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = selectedPaths.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina selezionati")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (connected && !selectionMode && activeFilter == null) {
                FloatingActionButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuova cartella")
                }
            }
        },
        bottomBar = {
            if (mode == BrowserMode.PICK_FOLDER && onPickPath != null && connected) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { onPickPath(currentPath) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(if (currentPath.isBlank()) "Usa la radice" else "Usa questa cartella")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showFilterBar) {
                FilterChipsBar(activeFilter = activeFilter, onFilterSelected = { activeFilter = it })
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    connectionError != null -> ErrorState(connectionError!!)
                    loading || filterLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    loadError != null -> ErrorState(loadError!!)
                    displayedEntries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (activeFilter != null) "Nessun file trovato per questo tipo" else "Cartella vuota",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    mode == BrowserMode.VIEW_GALLERY -> GalleryGrid(
                        entries = displayedEntries,
                        thumbnailCache = thumbnailCache,
                        selectionMode = selectionMode,
                        selectedPaths = selectedPaths,
                        onLoadThumbnail = { entry -> loadThumbnail(entry) },
                        onTap = { entry -> handleTap(entry) },
                        onLongPress = { entry -> handleLongPress(entry) }
                    )
                    else -> LazyColumn {
                        items(displayedEntries, key = { it.path }) { entry ->
                            RemoteEntryRow(
                                entry = entry,
                                thumbnail = thumbnailCache[entry.path],
                                onLoadThumbnail = { loadThumbnail(entry) },
                                onClick = {
                                    when {
                                        entry.isDirectory -> currentPath = entry.path
                                        entry.isImage() -> viewingImageIndex = imageEntries.indexOfFirst { it.path == entry.path }
                                        else -> openExternally(entry)
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }

                openingFile?.let { entry -> BusyOverlay("Apertura ${entry.name}…") }
                if (deleting) BusyOverlay("Eliminazione in corso…")
                if (sharingBusy) BusyOverlay("Preparazione condivisione…")
                if (downloadingBusy) BusyOverlay("Salvataggio in corso…")
            }
        }
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val newPath = if (currentPath.isBlank()) name else "$currentPath/$name"
                        client.mkdirs(newPath)
                    }
                    withContext(Dispatchers.Main) { reloadCurrentFolder() }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val folderCount = displayedEntries.count { selectedPaths.contains(it.path) && it.isDirectory }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare ${selectedPaths.size} elementi?") },
            text = {
                Text(
                    if (folderCount > 0)
                        "Verranno eliminati definitivamente i file e le cartelle selezionati, incluso tutto il contenuto delle cartelle. L'operazione non può essere annullata."
                    else
                        "I file selezionati verranno eliminati definitivamente dall'HDD. L'operazione non può essere annullata."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; performDelete() }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") } }
        )
    }

    viewingImageIndex?.let { startIndex ->
        ImagePagerViewer(
            client = client,
            images = imageEntries,
            startIndex = startIndex,
            onShare = { entry -> performShare(listOf(entry)) },
            onDownload = { entry -> downloadSingle(entry) },
            onDismiss = { viewingImageIndex = null }
        )
    }
}

@Composable
private fun FilterChipsBar(activeFilter: FileCategory?, onFilterSelected: (FileCategory?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = activeFilter == null, onClick = { onFilterSelected(null) }, label = { Text("Tutti") })
        FileCategory.entries.forEach { category ->
            FilterChip(
                selected = activeFilter == category,
                onClick = { onFilterSelected(category) },
                label = { Text(filterLabel(category)) }
            )
        }
    }
}

private fun filterLabel(category: FileCategory) = when (category) {
    FileCategory.IMMAGINI -> "Foto"
    FileCategory.VIDEO -> "Video"
    FileCategory.AUDIO -> "Audio"
    FileCategory.DOCUMENTI -> "Documenti"
}

@Composable
private fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(text)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGrid(
    entries: List<RemoteEntry>,
    thumbnailCache: Map<String, Bitmap?>,
    selectionMode: Boolean,
    selectedPaths: List<String>,
    onLoadThumbnail: (RemoteEntry) -> Unit,
    onTap: (RemoteEntry) -> Unit,
    onLongPress: (RemoteEntry) -> Unit
) {
    val folders = entries.filter { it.isDirectory }
    val images = entries.filter { it.isImage() }
    val videos = entries.filter { it.isVideo() }
    val audioFiles = entries.filter { it.isAudio() }
    val mediaTiles = images + videos + audioFiles
    val otherFiles = entries.filter { !it.isDirectory && !it.isImage() && !it.isVideo() && !it.isAudio() }

    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        if (folders.isNotEmpty()) {
            items(folders, key = { it.path }, span = { GridItemSpan(3) }) { folder ->
                val selected = selectedPaths.contains(folder.path)
                ListItem(
                    modifier = Modifier.combinedClickable(onClick = { onTap(folder) }, onLongClick = { onLongPress(folder) }),
                    leadingContent = {
                        if (selectionMode) SelectionIndicator(selected) else Icon(Icons.Default.Folder, contentDescription = null)
                    },
                    headlineContent = { Text(folder.name) }
                )
                Divider()
            }
        }

        items(mediaTiles, key = { it.path }) { entry ->
            val selected = selectedPaths.contains(entry.path)
            when {
                entry.isImage() -> {
                    LaunchedEffect(entry.path) { onLoadThumbnail(entry) }
                    val bitmap = thumbnailCache[entry.path]
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .combinedClickable(onClick = { onTap(entry) }, onLongClick = { onLongPress(entry) })
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = entry.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                        if (selectionMode) SelectionBadge(selected, Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                }
                entry.isVideo() -> MediaTile(
                    entry = entry, icon = Icons.Default.PlayCircle,
                    background = Color(0xFF2B2B2B), contentColor = Color.White,
                    selectionMode = selectionMode, selected = selected,
                    onClick = { onTap(entry) }, onLongClick = { onLongPress(entry) }
                )
                else -> MediaTile(
                    entry = entry, icon = Icons.Default.AudioFile,
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    selectionMode = selectionMode, selected = selected,
                    onClick = { onTap(entry) }, onLongClick = { onLongPress(entry) }
                )
            }
        }

        if (otherFiles.isNotEmpty()) {
            items(otherFiles, key = { it.path }, span = { GridItemSpan(3) }) { file ->
                val selected = selectedPaths.contains(file.path)
                ListItem(
                    modifier = Modifier.combinedClickable(onClick = { onTap(file) }, onLongClick = { onLongPress(file) }),
                    leadingContent = {
                        if (selectionMode) SelectionIndicator(selected) else Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                    },
                    headlineContent = { Text(file.name) },
                    supportingContent = { Text(formatSize(file.size)) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    Icon(
        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (selected) "Selezionato" else "Non selezionato",
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).background(Color.Black.copy(alpha = 0.4f), shape = androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    entry: RemoteEntry,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    contentColor: Color,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                entry.name, color = contentColor, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (selectionMode) SelectionBadge(selected, Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteEntryRow(
    entry: RemoteEntry,
    thumbnail: Bitmap?,
    onLoadThumbnail: () -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(entry.path) {
        if (entry.isImage()) onLoadThumbnail()
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            when {
                entry.isDirectory -> Icon(Icons.Default.Folder, contentDescription = null)
                entry.isVideo() -> Icon(Icons.Default.PlayCircle, contentDescription = null)
                entry.isAudio() -> Icon(Icons.Default.AudioFile, contentDescription = null)
                entry.isImage() && thumbnail != null -> Image(
                    bitmap = thumbnail.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(40.dp), contentScale = ContentScale.Crop
                )
                else -> Icon(Icons.Default.InsertDriveFile, contentDescription = null)
            }
        },
        headlineContent = { Text(entry.name) },
        supportingContent = { if (!entry.isDirectory) Text(formatSize(entry.size)) }
    )
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova cartella") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome cartella") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text("Crea") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

/**
 * Visualizzatore a schermo intero con scorrimento swipe. Ogni pagina scarica e decodifica
 * la propria immagine in modo indipendente (senza accumulare byte grezzi in una cache
 * condivisa): quando la pagina esce dalla composizione, la memoria viene liberata dal
 * normale ciclo di vita di Compose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePagerViewer(
    client: RemoteClient,
    images: List<RemoteEntry>,
    startIndex: Int,
    onShare: (RemoteEntry) -> Unit,
    onDownload: (RemoteEntry) -> Unit,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, images.size - 1)) { images.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val entry = images[page]
                val bitmapState = produceState<Bitmap?>(initialValue = null, entry.path) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = client.downloadBytes(entry.path)
                            decodeSampledBitmap(bytes, 2000)
                        }.getOrNull()
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bitmap = bitmapState.value
                    if (bitmap == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Image(
                            bitmap = bitmap.asImageBitmap(), contentDescription = entry.name,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Surface(color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Text(
                    text = "${images[pagerState.currentPage].name}  (${pagerState.currentPage + 1}/${images.size})",
                    color = Color.White, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
                )
            }

            Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                IconButton(onClick = { onDownload(images[pagerState.currentPage]) }) {
                    Icon(Icons.Default.Download, contentDescription = "Scarica sul telefono", tint = Color.White)
                }
                IconButton(onClick = { onShare(images[pagerState.currentPage]) }) {
                    Icon(Icons.Default.Share, contentDescription = "Condividi", tint = Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                }
            }
        }
    }
}

private suspend fun openFileExternally(context: Context, client: RemoteClient, entry: RemoteEntry) {
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "opened_files").apply { mkdirs() }
            val localFile = File(dir, entry.name)
            localFile.outputStream().use { out -> client.download(entry.path, out) }

            val extension = entry.name.substringAfterLast('.', "")
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "Nessuna app installata può aprire questo tipo di file (${extension.uppercase()})", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Errore nell'apertura: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private suspend fun shareFilesExternally(context: Context, client: RemoteClient, entries: List<RemoteEntry>) {
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared_files").apply { mkdirs() }
            val uris = ArrayList<android.net.Uri>()
            for (entry in entries) {
                val localFile = File(dir, entry.name)
                localFile.outputStream().use { out -> client.download(entry.path, out) }
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile))
            }
            if (uris.isEmpty()) return@withContext

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        entries.first().name.substringAfterLast('.', "").lowercase()
                    ) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Condividi con").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Errore nella condivisione: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun deleteRecursively(client: RemoteClient, entry: RemoteEntry) {
    if (entry.isDirectory) {
        val children = client.listFiles(entry.path)
        for (child in children) deleteRecursively(client, child)
        client.delete(entry.path)
    } else {
        client.delete(entry.path)
    }
}

private fun scanForCategory(client: RemoteClient, rootPath: String, category: FileCategory): List<RemoteEntry> {
    val result = mutableListOf<RemoteEntry>()
    val children = client.listFiles(rootPath)
    for (child in children) {
        if (child.isDirectory) {
            result += scanForCategory(client, child.path, category)
        } else if (categoryForFileName(child.name) == category) {
            result += child
        }
    }
    return result
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** Decodifica un'immagine da byte array ridimensionandola, per non caricarla mai a piena risoluzione in memoria. */
private fun decodeSampledBitmap(data: ByteArray, reqSize: Int): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)

        var sampleSize = 1
        val halfWidth = boundsOptions.outWidth / 2
        val halfHeight = boundsOptions.outHeight / 2
        while (halfWidth / sampleSize >= reqSize && halfHeight / sampleSize >= reqSize) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }
}
