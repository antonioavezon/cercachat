package cl.antonioavezon.cercachat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cl.antonioavezon.cercachat.audio.VoicePlayer
import cl.antonioavezon.cercachat.audio.VoiceRecorder
import cl.antonioavezon.cercachat.session.ChatLine
import cl.antonioavezon.cercachat.session.DeliveryStatus
import cl.antonioavezon.cercachat.session.LineKind
import cl.antonioavezon.cercachat.session.SessionController
import cl.antonioavezon.cercachat.session.SessionSnapshot
import cl.antonioavezon.cercachat.storage.SettingsRepository
import cl.antonioavezon.cercachat.ui.formatSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    snapshot: SessionSnapshot,
    session: SessionController,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState(
        initial = cl.antonioavezon.cercachat.storage.UiSettings.Default
    )
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer() }
    var recording by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewDuration by remember { mutableStateOf(0L) }
    var recordMs by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        session.setChatVisible(true)
        onDispose {
            session.setChatVisible(false)
            recorder.discard()
            player.release()
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 2
        }
    }
    var pendingNew by remember { mutableStateOf(false) }
    LaunchedEffect(snapshot.messages.size) {
        if (nearEnd) {
            pendingNew = false
            val last = snapshot.messages.lastIndex
            if (last >= 0) listState.animateScrollToItem(last)
        } else if (snapshot.messages.isNotEmpty()) {
            pendingNew = true
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { session.sendFile(it) }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recorder.start()
            recording = true
            recordMs = 0
        } else {
            Toast.makeText(context, "Se necesita el micrófono para notas de voz.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            val start = System.currentTimeMillis()
            while (recording) {
                recordMs = System.currentTimeMillis() - start
                delay(200)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(settings.chatBgColor))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (snapshot.connected) "Conectado · ${snapshot.peerAlias.ifBlank { "Compañero" }}"
                        else snapshot.statusText,
                        color = if (snapshot.connected) Color(0xFF0B6E4F) else MaterialTheme.colorScheme.error,
                    )
                    snapshot.hostIp?.let { Text("Red local $it", style = MaterialTheme.typography.bodySmall) }
                }
                OutlinedButton(
                    onClick = { session.closeConversation() },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Cerrar conversación" },
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Text("Cerrar conversación", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(snapshot.messages, key = { it.id }) { line ->
                    MessageBubble(
                        line = line,
                        settings = settings,
                        onCopy = { copyText(context, line.text) },
                        onRetry = { session.retryMessage(line.id) },
                        onCancel = { session.cancelTransfer(line.id) },
                        onOpen = { path -> openFile(context, File(path)) },
                        onPlay = { path -> player.toggle(File(path)) },
                        playingPath = player.playingPath,
                    )
                }
            }
            if (pendingNew) {
                FilledTonalButton(
                    onClick = {
                        pendingNew = false
                        val last = snapshot.messages.lastIndex
                        if (last >= 0) scope.launch { listState.animateScrollToItem(last) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).heightIn(min = 48.dp),
                ) {
                    Text("Nuevos mensajes")
                }
            }
        }

        if (recording) {
            Text("Grabando… ${recordMs / 1000}s", modifier = Modifier.padding(horizontal = 16.dp))
        }
        previewFile?.let { file ->
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nota ${previewDuration / 1000}s")
                IconButton(onClick = { player.toggle(file) }, modifier = Modifier.semantics { contentDescription = "Escuchar nota de voz" }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                Button(onClick = {
                    session.sendVoice(file, previewDuration)
                    previewFile = null
                }) { Text("Enviar nota") }
                OutlinedButton(onClick = {
                    file.delete()
                    previewFile = null
                }) { Text("Descartar") }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Adjuntar archivo" },
            ) { Icon(Icons.Default.Add, contentDescription = null) }
            IconButton(
                onClick = {
                    if (recording) {
                        val result = recorder.stop()
                        recording = false
                        previewFile = result?.first
                        previewDuration = result?.second ?: 0L
                    } else {
                        micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = if (recording) "Detener grabación" else "Grabar nota de voz" },
            ) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje") },
                maxLines = 6,
            )
            IconButton(
                onClick = {
                    session.sendText(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && snapshot.connected,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Enviar mensaje" },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    line: ChatLine,
    settings: cl.antonioavezon.cercachat.storage.UiSettings,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (String) -> Unit,
    playingPath: String?,
) {
    val bubble = Color(if (line.mine) settings.sentBubbleColor else settings.receivedBubbleColor)
    val textColor = Color(settings.textColor)
    val align = if (line.mine) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bubble, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = {}, onLongClick = onCopy)
                .padding(12.dp),
        ) {
            when (line.kind) {
                LineKind.TEXT -> Text(line.text, color = textColor, fontSize = (16 * settings.fontScale).sp)
                LineKind.FILE, LineKind.VOICE -> {
                    Text(line.fileName ?: "Archivo", color = textColor, fontSize = (16 * settings.fontScale).sp)
                    line.fileSize?.let { Text(formatSize(it), color = textColor, style = MaterialTheme.typography.bodySmall) }
                    if (line.transferActive || (line.progress > 0f && line.progress < 1f && line.status == DeliveryStatus.SENDING)) {
                        LinearProgressIndicator(progress = { line.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    line.durationMs?.takeIf { it > 0 }?.let { Text("${it / 1000}s", color = textColor) }
                }
            }
            Text(
                "${hora(line.sentAtMs)} · ${estado(line)}",
                color = textColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row {
            if (line.status == DeliveryStatus.ERROR) {
                TextButton48("Reintentar", onRetry)
            }
            if (line.transferActive) {
                TextButton48("Cancelar", onCancel)
            }
            if (line.kind == LineKind.FILE && line.localPath != null && line.status == DeliveryStatus.DELIVERED && !line.mine) {
                TextButton48("Abrir") { onOpen(line.localPath) }
            }
            if (line.kind == LineKind.VOICE && line.localPath != null) {
                TextButton48(if (playingPath == line.localPath) "Pausar" else "Reproducir") { onPlay(line.localPath) }
            }
        }
    }
}

@Composable
private fun TextButton48(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).padding(end = 4.dp)) {
        Text(label)
    }
}

private fun estado(line: ChatLine): String = when (line.status) {
    DeliveryStatus.SENDING -> if (line.mine) "Enviando" else "Recibiendo"
    DeliveryStatus.DELIVERED -> "Entregado"
    DeliveryStatus.ERROR -> line.error ?: "Error"
}

private fun hora(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("CercaChat", text))
    Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
}

private fun openFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Abrir archivo")) }
}
