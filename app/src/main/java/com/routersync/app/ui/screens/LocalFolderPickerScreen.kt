package com.routersync.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Selettore delle cartelle/file da includere nella sincronizzazione, con anteprime reali
 * per le immagini, come nella galleria dell'HDD. Di default tutto è selezionato: l'utente
 * deseleziona solo ciò che non vuole sincronizzare.
 *
 * Se un elemento ha una cartella superiore già esclusa, il suo stato non è modificabile
 * direttamente: va prima ri-inclusa la cartella superiore, poi rifinita la selezione da lì.
 * Questa semplificazione evita una complessità sproporzionata nel tenere traccia di
 * eccezioni annidate, mantenendo comunque il caso d'uso principale (alcune sottocartelle sì,
 * altre no) semplice e diretto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFolderPickerContent(
    rootUri: Uri,
    initialExcluded: Set<String>,
    onDone: (Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var excluded by remember { mutableStateOf(initialExcluded) }
    var currentRelativePath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val thumbnailCache = remember { mutableStateMapOf<String, Bitmap?>() }

    val rootDoc = remember(rootUri) { DocumentFile.fromTreeUri(context, rootUri) }

    fun currentDir(): DocumentFile? {
        if (currentRelativePath.isBlank()) return rootDoc
        var dir = rootDoc
        for (segment in currentRelativePath.split("/")) {
            dir = dir?.findFile(segment)
        }
        return dir
    }

    LaunchedEffect(currentRelativePath) {
        loading = true
        withContext(Dispatchers.IO) {
            val dir = currentDir()
            entries = dir?.listFiles()
                ?.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
                ?.toList() ?: emptyList()
        }
        loading = false
    }

    fun relativePathOf(child: DocumentFile): String {
        val name = child.name ?: ""
        return if (currentRelativePath.isBlank()) name else "$currentRelativePath/$name"
    }

    fun ancestorExcluded(relativePath: String): Boolean =
        excluded.any { relativePath != it && relativePath.startsWith("$it/") }

    fun toggle(relativePath: String) {
        excluded = if (relativePath in excluded) excluded - relativePath else excluded + relativePath
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (currentRelativePath.isBlank()) "Scegli cosa sincronizzare" else currentRelativePath) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentRelativePath.isBlank()) onCancel()
                            else currentRelativePath = currentRelativePath.substringBeforeLast("/", "")
                        }) {
                            Icon(if (currentRelativePath.isBlank()) Icons.Default.Close else Icons.Default.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            val allRelPaths = entries.map { relativePathOf(it) }
                            val allSelected = allRelPaths.none { it in excluded }
                            excluded = if (allSelected) {
                                excluded + allRelPaths // deseleziona tutto in questa cartella
                            } else {
                                excluded - allRelPaths.toSet() // seleziona tutto in questa cartella
                            }
                        }) { Text("Sel./desel. tutto") }
                    }
                )
            },
            bottomBar = {
                Surface(shadowElevation = 8.dp) {
                    Button(onClick = { onDone(excluded) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Conferma selezione")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cartella vuota", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                        val folders = entries.filter { it.isDirectory }
                        val files = entries.filter { !it.isDirectory }

                        if (folders.isNotEmpty()) {
                            items(folders, span = { GridItemSpan(3) }) { folder ->
                                val relPath = relativePathOf(folder)
                                val lockedByAncestor = ancestorExcluded(relPath)
                                val selected = relPath !in excluded && !lockedByAncestor
                                ListItem(
                                    modifier = Modifier.clickable { currentRelativePath = relPath },
                                    leadingContent = {
                                        Icon(
                                            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (lockedByAncestor) MaterialTheme.colorScheme.outline
                                                else if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.clickable(enabled = !lockedByAncestor) { toggle(relPath) }
                                        )
                                    },
                                    headlineContent = { Text(folder.name ?: "") },
                                    supportingContent = if (lockedByAncestor) {
                                        { Text("Cartella superiore esclusa", style = MaterialTheme.typography.labelSmall) }
                                    } else null,
                                    trailingContent = { Icon(Icons.Default.Folder, contentDescription = null) }
                                )
                                Divider()
                            }
                        }

                        items(files) { file ->
                            val relPath = relativePathOf(file)
                            val lockedByAncestor = ancestorExcluded(relPath)
                            val selected = relPath !in excluded && !lockedByAncestor
                            val isImage = (file.type ?: "").startsWith("image/")

                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .padding(1.dp)
                                    .clickable(enabled = !lockedByAncestor) { toggle(relPath) }
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (isImage) {
                                    LaunchedEffect(file.uri) {
                                        if (!thumbnailCache.containsKey(relPath)) {
                                            scope.launch(Dispatchers.IO) {
                                                val bmp = runCatching {
                                                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                                                        val bytes = input.readBytes()
                                                        decodeSampledLocalBitmap(bytes, 150)
                                                    }
                                                }.getOrNull()
                                                thumbnailCache[relPath] = bmp
                                            }
                                        }
                                    }
                                    val bmp = thumbnailCache[relPath]
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(), contentDescription = file.name,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(32.dp))
                                    }
                                }

                                Box(
                                    Modifier.size(22.dp).align(Alignment.TopEnd).padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), shape = androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeSampledLocalBitmap(data: ByteArray, reqSize: Int): Bitmap? {
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
