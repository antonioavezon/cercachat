package cl.antonioavezon.cercachat.session

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import cl.antonioavezon.cercachat.notify.AlertPlayer
import cl.antonioavezon.cercachat.notify.SessionForegroundService
import cl.antonioavezon.cercachat.protocol.CryptoIds
import cl.antonioavezon.cercachat.protocol.FlatJson
import cl.antonioavezon.cercachat.protocol.Frame
import cl.antonioavezon.cercachat.protocol.FrameType
import cl.antonioavezon.cercachat.protocol.Protocol
import cl.antonioavezon.cercachat.protocol.QrPayload
import cl.antonioavezon.cercachat.protocol.TransferLimits
import cl.antonioavezon.cercachat.storage.IncomingFileStore
import cl.antonioavezon.cercachat.storage.SavedFilesRepository
import cl.antonioavezon.cercachat.storage.SettingsRepository
import cl.antonioavezon.cercachat.transport.FileChunks
import cl.antonioavezon.cercachat.transport.HostIdentity
import cl.antonioavezon.cercachat.transport.LocalHotspotHost
import cl.antonioavezon.cercachat.transport.PairingTransport
import cl.antonioavezon.cercachat.transport.SecureSocketSession
import cl.antonioavezon.cercachat.transport.SessionEvent
import cl.antonioavezon.cercachat.transport.TlsFactory
import cl.antonioavezon.cercachat.transport.WifiSpecifierClient
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionController(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val fileStore: IncomingFileStore,
    private val savedFiles: SavedFilesRepository,
    private val alerts: AlertPlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SessionSnapshot())
    val state: StateFlow<SessionSnapshot> = _state.asStateFlow()

    private var hotspot: LocalHotspotHost? = null
    private var reservation: android.net.wifi.WifiManager.LocalOnlyHotspotReservation? = null
    private var identity: HostIdentity? = null
    private var hostReady: cl.antonioavezon.cercachat.transport.HotspotReady? = null
    private var currentToken: String? = null
    private var tokenUsed = false
    private var sessionClosed = true
    private var acceptJob: Job? = null
    private var wifiClient: WifiSpecifierClient? = null
    private var link: SecureSocketSession? = null
    private var eventJob: Job? = null
    private val ackWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val incomingWriters = ConcurrentHashMap<String, IncomingWrite>()
    private val cancelledTransfers = ConcurrentHashMap.newKeySet<String>()
    private var chatVisible = false

    init {
        restorePendingRetention()
    }

    fun setChatVisible(visible: Boolean) {
        chatVisible = visible
    }

    fun startHost() {
        scope.launch {
            mutex.withLock {
                tearDownNetworkLocked(notifyPeer = false, promptRetention = false)
                sessionClosed = false
                tokenUsed = false
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Preparing,
                        role = SessionRole.HOST,
                        statusText = "Preparando conexión",
                        errorMessage = null,
                        qrContent = null,
                        messages = emptyList(),
                    )
                }
            }
            try {
                val host = LocalHotspotHost(appContext)
                hotspot = host
                val ready = host.startSuspend()
                val id = TlsFactory.createHostIdentity()
                identity = id
                hostReady = ready
                reservation = ready.reservation
                publishNewQr(ready, id)
                SessionForegroundService.start(appContext)
                acceptPeer()
            } catch (e: Exception) {
                fail(e.message ?: "No se pudo preparar la red local.")
            }
        }
    }

    fun regenerateQr() {
        scope.launch {
            mutex.withLock {
                val ready = hostReady
                val id = identity
                if (ready == null || id == null || sessionClosed) {
                    fail("No hay una red local activa para regenerar el QR.")
                    return@launch
                }
                tokenUsed = false
                acceptJob?.cancel()
                publishNewQr(ready, id)
            }
            acceptPeer()
        }
    }

    fun cancelPairing() {
        scope.launch {
            mutex.withLock {
                tearDownNetworkLocked(notifyPeer = false, promptRetention = false)
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Idle,
                        statusText = "Sin conexión",
                        qrContent = null,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    fun startClient(rawQr: String) {
        scope.launch {
            val limits = currentLimits()
            val decoded = QrPayload.decode(rawQr, System.currentTimeMillis(), limits)
            if (decoded is cl.antonioavezon.cercachat.protocol.QrDecodeResult.Invalid) {
                fail(decoded.message)
                return@launch
            }
            val payload = (decoded as cl.antonioavezon.cercachat.protocol.QrDecodeResult.Success).payload
            mutex.withLock {
                tearDownNetworkLocked(notifyPeer = false, promptRetention = false)
                sessionClosed = false
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Connecting,
                        role = SessionRole.CLIENT,
                        statusText = "Conectando",
                        errorMessage = null,
                        messages = emptyList(),
                        hostIp = payload.host,
                    )
                }
            }
            try {
                val client = WifiSpecifierClient(appContext)
                wifiClient = client
                SessionForegroundService.start(appContext)
                val net = client.connect(payload)
                val settings = settingsRepository.current()
                val auth = PairingTransport.clientConnect(net.network, payload.copy(host = net.hostIpv4), settings.localAlias, limits)
                becomeConnected(auth.socket, auth.peerAlias, auth.sessionId, SessionRole.CLIENT, net.hostIpv4)
            } catch (e: Exception) {
                fail(e.message ?: "No se pudo conectar al compañero.")
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val limits = currentLimits()
            if (trimmed.length > limits.maxTextChars) {
                fail("El mensaje supera el límite de ${limits.maxTextChars} caracteres. No se truncó.")
                return@launch
            }
            val id = CryptoIds.messageId()
            val now = System.currentTimeMillis()
            upsertLine(
                ChatLine(
                    id = id,
                    kind = LineKind.TEXT,
                    mine = true,
                    text = trimmed,
                    sentAtMs = now,
                    status = DeliveryStatus.SENDING,
                )
            )
            transmitText(id, trimmed, now)
        }
    }

    fun retryMessage(id: String) {
        scope.launch {
            val line = _state.value.messages.firstOrNull { it.id == id } ?: return@launch
            upsertLine(line.copy(status = DeliveryStatus.SENDING, error = null, progress = 0f))
            when (line.kind) {
                LineKind.TEXT -> transmitText(line.id, line.text, line.sentAtMs)
                LineKind.FILE, LineKind.VOICE -> {
                    val path = line.localPath ?: return@launch
                    sendFileFromPath(line.id, File(path), line.fileName ?: File(path).name, line.kind, line.durationMs)
                }
            }
        }
    }

    fun sendFile(uri: Uri) {
        scope.launch { sendFromUri(uri, LineKind.FILE, null) }
    }

    fun sendVoice(file: File, durationMs: Long) {
        scope.launch {
            val sessionId = _state.value.let { it.hostIp?.let { _ -> currentSessionId() } } ?: currentSessionId()
            val dest = fileStore.createIncoming(sessionId, file.name)
            file.copyTo(dest, overwrite = true)
            sendFileFromPath(CryptoIds.messageId(), dest, dest.name, LineKind.VOICE, durationMs)
        }
    }

    fun cancelTransfer(id: String) {
        cancelledTransfers.add(id)
        scope.launch {
            runCatching { link?.send(jsonFrame(FrameType.FILE_CANCEL, FlatJson.obj("id" to id))) }
            updateLine(id) { it.copy(transferActive = false, status = DeliveryStatus.ERROR, error = "Transferencia cancelada") }
        }
    }

    fun closeConversation() {
        scope.launch {
            mutex.withLock {
                tearDownNetworkLocked(notifyPeer = true, promptRetention = true)
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Idle,
                        connected = false,
                        statusText = "Conversación cerrada",
                        qrContent = null,
                        messages = emptyList(),
                    )
                }
            }
        }
    }

    fun decideRetention(keep: Boolean) {
        scope.launch {
            val prompt = _state.value.retention ?: return@launch
            if (keep) {
                savedFiles.addAll(fileStore.keepSession(prompt.sessionId))
            } else {
                fileStore.deleteSession(prompt.sessionId)
            }
            _state.update { it.copy(retention = null) }
        }
    }

    private fun acceptPeer() {
        acceptJob?.cancel()
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(phase = ConnectionPhase.WaitingPeer, statusText = "Esperando compañero") }
                val ready = hostReady ?: return@launch
                val idn = identity ?: return@launch
                val token = currentToken ?: return@launch
                val settings = settingsRepository.current()
                val limits = currentLimits()
                val expires = _state.value.qrExpiresAtMs ?: (System.currentTimeMillis() + limits.qrTtlMs)
                val auth = PairingTransport.hostAccept(
                    bindIpv4 = ready.hostIpv4,
                    port = Protocol.DEFAULT_PORT,
                    identity = idn,
                    expectedToken = token,
                    sessionId = currentSessionId(),
                    localAlias = settings.localAlias,
                    expiresAtMs = expires,
                    tokenAlreadyUsed = { tokenUsed },
                    markTokenUsed = { tokenUsed = true },
                    limits = limits,
                )
                becomeConnected(auth.socket, auth.peerAlias, auth.sessionId, SessionRole.HOST, ready.hostIpv4)
            } catch (e: Exception) {
                if (!sessionClosed && _state.value.phase != ConnectionPhase.Connected) {
                    fail(e.message ?: "Nadie se conectó o el emparejamiento falló.")
                }
            }
        }
    }

    private suspend fun becomeConnected(
        socket: javax.net.ssl.SSLSocket,
        peerAlias: String,
        sessionId: String,
        role: SessionRole,
        hostIp: String,
    ) {
        mutex.withLock {
            val session = SecureSocketSession(socket, scope, currentLimits())
            link = session
            session.start()
            eventJob?.cancel()
            eventJob = scope.launch { session.events.collect { handleEvent(it) } }
            _state.update {
                it.copy(
                    phase = ConnectionPhase.Connected,
                    role = role,
                    connected = true,
                    statusText = "Conectado",
                    peerAlias = peerAlias.ifBlank { "Compañero" },
                    hostIp = hostIp,
                    qrContent = null,
                    errorMessage = null,
                )
            }
            rememberSessionId(sessionId)
            SessionForegroundService.start(appContext)
        }
    }

    private suspend fun handleEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.Disconnected -> {
                _state.update {
                    it.copy(
                        connected = false,
                        phase = ConnectionPhase.Error,
                        statusText = "Desconectado",
                        errorMessage = event.message,
                    )
                }
            }
            is SessionEvent.FrameIn -> handleFrame(event.frame)
        }
    }

    private suspend fun handleFrame(frame: Frame) {
        when (frame.type) {
            FrameType.TEXT -> {
                val map = FlatJson.parse(frame.payload.toString(StandardCharsets.UTF_8))
                val id = FlatJson.string(map, "id")
                val text = FlatJson.string(map, "text")
                val at = FlatJson.long(map, "at")
                upsertLine(
                    ChatLine(
                        id = id,
                        kind = LineKind.TEXT,
                        mine = false,
                        text = text,
                        sentAtMs = at,
                        status = DeliveryStatus.DELIVERED,
                    )
                )
                alerts.announceIncoming(chatVisible)
                runCatching { link?.send(jsonFrame(FrameType.TEXT_ACK, FlatJson.obj("id" to id))) }
            }
            FrameType.TEXT_ACK -> {
                val id = FlatJson.string(FlatJson.parse(frame.payload.toString(StandardCharsets.UTF_8)), "id")
                updateLine(id) { it.copy(status = DeliveryStatus.DELIVERED) }
                ackWaiters.remove(id)?.complete(true)
            }
            FrameType.FILE_META -> handleFileMeta(frame)
            FrameType.FILE_CHUNK -> handleFileChunk(frame)
            FrameType.FILE_END -> handleFileEnd(frame)
            FrameType.FILE_ACK -> completeAck(frame, true)
            FrameType.FILE_NACK -> completeAck(frame, false)
            FrameType.FILE_CANCEL -> {
                val id = FlatJson.string(FlatJson.parse(frame.payload.toString(StandardCharsets.UTF_8)), "id")
                incomingWriters.remove(id)?.closeAbort()
                updateLine(id) { it.copy(transferActive = false, status = DeliveryStatus.ERROR, error = "El compañero canceló") }
            }
            FrameType.CLOSE -> {
                mutex.withLock {
                    tearDownNetworkLocked(notifyPeer = false, promptRetention = true)
                    _state.update {
                        it.copy(
                            phase = ConnectionPhase.Idle,
                            connected = false,
                            statusText = "El compañero cerró la conversación",
                            messages = emptyList(),
                            qrContent = null,
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    private suspend fun handleFileMeta(frame: Frame) {
        val map = FlatJson.parse(frame.payload.toString(StandardCharsets.UTF_8))
        val id = FlatJson.string(map, "id")
        val name = FlatJson.string(map, "name")
        val size = FlatJson.long(map, "size")
        val kind = if (FlatJson.optString(map, "kind") == "voice") LineKind.VOICE else LineKind.FILE
        val duration = FlatJson.optString(map, "dur")?.toLongOrNull()
        val limits = currentLimits()
        if (size <= 0L) {
            runCatching { link?.send(jsonFrame(FrameType.FILE_NACK, FlatJson.obj("id" to id, "why" to "empty"))) }
            return
        }
        if (size > limits.maxFileBytes) {
            runCatching { link?.send(jsonFrame(FrameType.FILE_NACK, FlatJson.obj("id" to id, "why" to "limit"))) }
            upsertLine(
                ChatLine(
                    id = id,
                    kind = kind,
                    mine = false,
                    sentAtMs = System.currentTimeMillis(),
                    status = DeliveryStatus.ERROR,
                    fileName = name,
                    fileSize = size,
                    error = "El archivo supera el límite configurado y no se truncó.",
                )
            )
            return
        }
        if (fileStore.availableBytes() < size + 1_000_000L) {
            runCatching { link?.send(jsonFrame(FrameType.FILE_NACK, FlatJson.obj("id" to id, "why" to "space"))) }
            return
        }
        val dest = fileStore.createIncoming(currentSessionId(), name)
        incomingWriters[id] = IncomingWrite(dest, size)
        fileStore.markPending(currentSessionId())
        upsertLine(
            ChatLine(
                id = id,
                kind = kind,
                mine = false,
                sentAtMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENDING,
                fileName = dest.name,
                fileSize = size,
                progress = 0f,
                transferActive = true,
                durationMs = duration,
            )
        )
    }

    private fun handleFileChunk(frame: Frame) {
        val (id, offset, data) = FileChunks.decode(frame.payload)
        val writer = incomingWriters[id] ?: return
        writer.write(offset, data)
        updateLine(id) {
            val total = it.fileSize ?: 1L
            it.copy(progress = (writer.written.toFloat() / total.toFloat()).coerceIn(0f, 1f))
        }
    }

    private suspend fun handleFileEnd(frame: Frame) {
        val map = FlatJson.parse(frame.payload.toString(StandardCharsets.UTF_8))
        val id = FlatJson.string(map, "id")
        val sha = FlatJson.string(map, "sha")
        val writer = incomingWriters.remove(id)
        if (writer == null) {
            runCatching { link?.send(jsonFrame(FrameType.FILE_NACK, FlatJson.obj("id" to id, "why" to "missing"))) }
            return
        }
        val ok = writer.finish(sha)
        if (ok) {
            updateLine(id) {
                it.copy(
                    status = DeliveryStatus.DELIVERED,
                    progress = 1f,
                    transferActive = false,
                    localPath = writer.file.absolutePath,
                )
            }
            alerts.announceIncoming(chatVisible)
            runCatching { link?.send(jsonFrame(FrameType.FILE_ACK, FlatJson.obj("id" to id))) }
        } else {
            writer.file.delete()
            updateLine(id) {
                it.copy(status = DeliveryStatus.ERROR, transferActive = false, error = "Integridad SHA-256 no válida o archivo incompleto")
            }
            runCatching { link?.send(jsonFrame(FrameType.FILE_NACK, FlatJson.obj("id" to id, "why" to "hash"))) }
        }
    }

    private fun completeAck(frame: Frame, ok: Boolean) {
        val id = FlatJson.string(FlatJson.parse(frame.payload.toString(StandardCharsets.UTF_8)), "id")
        ackWaiters.remove(id)?.complete(ok)
        updateLine(id) {
            if (ok) it.copy(status = DeliveryStatus.DELIVERED, progress = 1f, transferActive = false)
            else it.copy(status = DeliveryStatus.ERROR, transferActive = false, error = "El compañero rechazó el archivo")
        }
    }

    private suspend fun transmitText(id: String, text: String, at: Long) {
        val session = link
        if (session == null) {
            updateLine(id) { it.copy(status = DeliveryStatus.ERROR, error = "Sin conexión") }
            return
        }
        val waiter = CompletableDeferred<Boolean>()
        ackWaiters[id] = waiter
        try {
            session.send(jsonFrame(FrameType.TEXT, FlatJson.obj("id" to id, "text" to text, "at" to at)))
            val ok = waiter.await()
            updateLine(id) {
                it.copy(status = if (ok) DeliveryStatus.DELIVERED else DeliveryStatus.ERROR)
            }
        } catch (e: Exception) {
            updateLine(id) { it.copy(status = DeliveryStatus.ERROR, error = e.message ?: "No se pudo enviar") }
        }
    }

    private suspend fun sendFromUri(uri: Uri, kind: LineKind, durationMs: Long?) {
        val info = queryUri(uri) ?: run {
            fail("No se pudo leer el archivo seleccionado.")
            return
        }
        val limits = currentLimits()
        if (info.second <= 0L) {
            fail("No se envían archivos vacíos.")
            return
        }
        if (info.second > limits.maxFileBytes) {
            fail("El archivo supera el límite de ${limits.maxFileBytes / (1024 * 1024)} MB y no se truncó.")
            return
        }
        val id = CryptoIds.messageId()
        upsertLine(
            ChatLine(
                id = id,
                kind = kind,
                mine = true,
                sentAtMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENDING,
                fileName = info.first,
                fileSize = info.second,
                transferActive = true,
                durationMs = durationMs,
            )
        )
        val session = link ?: run {
            updateLine(id) { it.copy(status = DeliveryStatus.ERROR, error = "Sin conexión", transferActive = false) }
            return
        }
        withContext(Dispatchers.IO) {
            streamOutgoing(id, info.first, info.second, kind, durationMs, session) { buf, max ->
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    var n: Int
                    while (input.read(buf, 0, max).also { n = it } != -1) {
                        emitChunk(buf, n)
                    }
                } ?: error("No se pudo abrir el archivo")
            }
        }
    }

    private suspend fun sendFileFromPath(id: String, file: File, name: String, kind: LineKind, durationMs: Long?) {
        upsertLine(
            ChatLine(
                id = id,
                kind = kind,
                mine = true,
                sentAtMs = System.currentTimeMillis(),
                status = DeliveryStatus.SENDING,
                fileName = name,
                fileSize = file.length(),
                localPath = file.absolutePath,
                transferActive = true,
                durationMs = durationMs,
            )
        )
        val session = link ?: run {
            updateLine(id) { it.copy(status = DeliveryStatus.ERROR, error = "Sin conexión", transferActive = false) }
            return
        }
        withContext(Dispatchers.IO) {
            streamOutgoing(id, name, file.length(), kind, durationMs, session) { buf, max ->
                file.inputStream().use { input ->
                    var n: Int
                    while (input.read(buf, 0, max).also { n = it } != -1) {
                        emitChunk(buf, n)
                    }
                }
            }
        }
    }

    private suspend fun streamOutgoing(
        id: String,
        name: String,
        size: Long,
        kind: LineKind,
        durationMs: Long?,
        session: SecureSocketSession,
        reader: suspend ChunkSink.(ByteArray, Int) -> Unit,
    ) {
        val limits = currentLimits()
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = 0L
        val sink = object : ChunkSink {
            override suspend fun emitChunk(buf: ByteArray, n: Int) {
                if (cancelledTransfers.contains(id)) throw TransferCancelled()
                digest.update(buf, 0, n)
                session.send(Frame(FrameType.FILE_CHUNK, FileChunks.encode(id, offset, buf.copyOf(n))))
                offset += n
                updateLine(id) { it.copy(progress = (offset.toFloat() / size.toFloat()).coerceIn(0f, 1f)) }
            }
        }
        try {
            session.send(
                jsonFrame(
                    FrameType.FILE_META,
                    FlatJson.obj(
                        "id" to id,
                        "name" to name,
                        "size" to size,
                        "sha" to "pending",
                        "kind" to if (kind == LineKind.VOICE) "voice" else "file",
                        "dur" to (durationMs ?: 0L),
                    )
                )
            )
            sink.reader(ByteArray(limits.chunkBytes), limits.chunkBytes)
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val waiter = CompletableDeferred<Boolean>()
            ackWaiters[id] = waiter
            session.send(jsonFrame(FrameType.FILE_END, FlatJson.obj("id" to id, "sha" to sha, "size" to offset)))
            val ok = waiter.await()
            updateLine(id) {
                it.copy(
                    status = if (ok) DeliveryStatus.DELIVERED else DeliveryStatus.ERROR,
                    transferActive = false,
                    progress = if (ok) 1f else it.progress,
                    error = if (ok) null else "No se confirmó la entrega",
                )
            }
        } catch (e: TransferCancelled) {
            updateLine(id) { it.copy(status = DeliveryStatus.ERROR, transferActive = false, error = "Cancelado") }
        } catch (e: Exception) {
            updateLine(id) { it.copy(status = DeliveryStatus.ERROR, transferActive = false, error = e.message ?: "Error de envío") }
        }
    }

    private fun publishNewQr(ready: cl.antonioavezon.cercachat.transport.HotspotReady, id: HostIdentity) {
        val limits = TransferLimits.fromMaxFileMb(settingsNow().maxFileMb)
        val token = CryptoIds.oneTimeToken()
        currentToken = token
        tokenUsed = false
        val sid = CryptoIds.sessionId()
        rememberSessionId(sid)
        val exp = System.currentTimeMillis() + limits.qrTtlMs
        val payload = QrPayload(
            protocolVersion = Protocol.VERSION,
            sessionId = sid,
            token = token,
            fingerprintSha256 = id.fingerprintSha256,
            ssid = ready.ssid,
            psk = ready.passphrase,
            host = ready.hostIpv4,
            port = Protocol.DEFAULT_PORT,
            expiresAtMs = exp,
        )
        val encoded = payload.encode()
        _state.update {
            it.copy(
                phase = ConnectionPhase.WaitingPeer,
                statusText = "Esperando compañero",
                qrContent = encoded,
                qrExpiresAtMs = exp,
                hostIp = ready.hostIpv4,
                errorMessage = null,
            )
        }
    }

    private suspend fun tearDownNetworkLocked(notifyPeer: Boolean, promptRetention: Boolean) {
        sessionClosed = true
        tokenUsed = true
        currentToken = null
        acceptJob?.cancel()
        acceptJob = null
        eventJob?.cancel()
        eventJob = null
        if (notifyPeer) {
            runCatching { link?.sendClose() }
        }
        link?.closeQuietly()
        link = null
        wifiClient?.release()
        wifiClient = null
        runCatching { reservation?.close() }
        reservation = null
        hostReady = null
        identity = null
        incomingWriters.values.forEach { it.closeAbort() }
        incomingWriters.clear()
        ackWaiters.values.forEach { it.complete(false) }
        ackWaiters.clear()
        val sid = currentSessionId()
        if (promptRetention) {
            val files = fileStore.listSessionFiles(sid)
            if (ClosePolicy.shouldPromptRetention(files.size)) {
                fileStore.markPending(sid)
                _state.update {
                    it.copy(
                        retention = RetentionPrompt(sid, files.size, files.sumOf { f -> f.length() }),
                    )
                }
            } else {
                fileStore.deleteSession(sid)
            }
        }
        SessionForegroundService.stop(appContext)
    }

    private fun restorePendingRetention() {
        val sid = fileStore.pendingSessionId() ?: return
        val files = fileStore.listSessionFiles(sid)
        if (ClosePolicy.pendingRetentionAfterCrash(files.isNotEmpty(), false)) {
            _state.update {
                it.copy(retention = RetentionPrompt(sid, files.size, files.sumOf { f -> f.length() }))
            }
        } else {
            fileStore.clearPending()
        }
    }

    private fun fail(message: String) {
        scope.launch {
            mutex.withLock {
                tearDownNetworkLocked(notifyPeer = false, promptRetention = _state.value.messages.any { it.localPath != null && !it.mine })
                _state.update {
                    it.copy(
                        phase = ConnectionPhase.Error,
                        connected = false,
                        statusText = "Error",
                        errorMessage = message,
                        qrContent = null,
                    )
                }
            }
        }
    }

    private fun upsertLine(line: ChatLine) {
        _state.update { snap ->
            val idx = snap.messages.indexOfFirst { it.id == line.id }
            val next = if (idx >= 0) snap.messages.toMutableList().also { it[idx] = line } else snap.messages + line
            snap.copy(messages = next)
        }
    }

    private fun updateLine(id: String, transform: (ChatLine) -> ChatLine) {
        _state.update { snap ->
            snap.copy(messages = snap.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun jsonFrame(type: FrameType, json: String) =
        Frame(type, json.toByteArray(StandardCharsets.UTF_8))

    private suspend fun currentLimits(): TransferLimits =
        TransferLimits.fromMaxFileMb(settingsRepository.current().maxFileMb)

    private fun settingsNow(): cl.antonioavezon.cercachat.storage.UiSettings {
        // Solo para el QR; si aún no hay valor en memoria usamos el predeterminado.
        return cl.antonioavezon.cercachat.storage.UiSettings.Default.copy(
            maxFileMb = _state.value.let { 100 }
        )
    }

    private var rememberedSessionId: String = CryptoIds.sessionId()
    private fun currentSessionId(): String = rememberedSessionId
    private fun rememberSessionId(id: String) {
        rememberedSessionId = id
    }

    private fun queryUri(uri: Uri): Pair<String, Long>? {
        val cursor = appContext.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIdx >= 0) it.getString(nameIdx) else "archivo"
            val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else -1L
            return name to size
        }
    }

    private class IncomingWrite(
        val file: File,
        private val expectedSize: Long,
    ) {
        private val out = FileOutputStream(file)
        private val digest = MessageDigest.getInstance("SHA-256")
        var written: Long = 0
            private set

        fun write(offset: Long, data: ByteArray) {
            if (offset != written) throw IllegalStateException("Bloque fuera de orden")
            out.write(data)
            digest.update(data)
            written += data.size
        }

        fun finish(expectedSha: String): Boolean {
            out.flush()
            out.close()
            if (written != expectedSize) return false
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            return sha == expectedSha.lowercase()
        }

        fun closeAbort() {
            runCatching { out.close() }
            file.delete()
        }
    }
}

private interface ChunkSink {
    suspend fun emitChunk(buf: ByteArray, n: Int)
}

private class TransferCancelled : Exception()
