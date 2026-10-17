package com.routersync.app.ui.screens

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.routersync.app.remote.RemoteClient
import com.routersync.app.remote.RemoteEntry
import com.routersync.app.remote.RemoteMediaDataSource
import kotlinx.coroutines.delay

/**
 * Lettore video a tutto schermo con streaming: il video parte subito leggendo dall'HDD solo i
 * pezzi che servono (a blocchi da 1 MB), senza aspettare il download completo. Funziona con SMB;
 * per gli altri protocolli o se il formato non è riproducibile mostra l'errore con il pulsante
 * per aprire il file con un'altra app (come prima).
 */
@Composable
fun VideoPlayerDialog(
    client: RemoteClient,
    entry: RemoteEntry,
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit
) {
    val player = remember { MediaPlayer() }
    val source = remember(entry.path) { RemoteMediaDataSource(client, entry.path, entry.size, 1024 * 1024) }

    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var videoRatio by remember { mutableStateOf(16f / 9f) }
    var error by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.setDisplay(null) }
            runCatching { player.release() }
            runCatching { source.close() }
        }
    }

    fun startPlayback() {
        if (started) return
        started = true
        try {
            player.setOnPreparedListener { mp ->
                durationMs = mp.duration
                prepared = true
                mp.start()
                playing = true
            }
            player.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) videoRatio = w.toFloat() / h.toFloat()
            }
            player.setOnCompletionListener {
                playing = false
                controlsVisible = true
            }
            player.setOnErrorListener { _, _, _ ->
                error = "Impossibile riprodurre questo video in streaming."
                true
            }
            player.setDataSource(source)
            player.prepareAsync()
        } catch (e: Exception) {
            error = "Impossibile riprodurre questo video in streaming."
        }
    }

    // Aggiorna la posizione mentre il video va (si ferma durante il trascinamento della barra)
    LaunchedEffect(prepared) {
        while (prepared) {
            if (!dragging) positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            delay(500)
        }
    }

    // I controlli spariscono dopo qualche secondo mentre il video è in riproduzione
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(3500)
            controlsVisible = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } }
        ) {
            AndroidView(
                modifier = Modifier.align(Alignment.Center).aspectRatio(videoRatio),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) {
                                runCatching { player.setDisplay(h) }
                                startPlayback()
                            }
                            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(h: SurfaceHolder) {
                                runCatching { player.setDisplay(null) }
                            }
                        })
                    }
                }
            )

            if (!prepared && error == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            error?.let { message ->
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(message, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenExternally) { Text("Apri con un'altra app") }
                }
            }

            if (controlsVisible || error != null) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                    }
                    Text(
                        entry.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (controlsVisible && prepared && error == null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        runCatching {
                            if (player.isPlaying) {
                                player.pause(); playing = false
                            } else {
                                player.start(); playing = true
                            }
                        }
                    }) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausa" else "Riproduci",
                            tint = Color.White
                        )
                    }
                    Text(formatVideoTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                        onValueChange = { dragging = true; positionMs = it.toInt() },
                        onValueChangeFinished = {
                            runCatching { player.seekTo(positionMs) }
                            dragging = false
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(formatVideoTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun formatVideoTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
