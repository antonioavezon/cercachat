package cl.antonioavezon.cercachat.transport

import cl.antonioavezon.cercachat.protocol.Frame
import cl.antonioavezon.cercachat.protocol.FramePriority
import cl.antonioavezon.cercachat.protocol.FrameType
import cl.antonioavezon.cercachat.protocol.Framing
import cl.antonioavezon.cercachat.protocol.ProtocolException
import cl.antonioavezon.cercachat.protocol.TransferLimits
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SecureSocketSession(
    private val socket: SSLSocket,
    private val scope: CoroutineScope,
    private val limits: TransferLimits,
) {
    private val controlQueue = Channel<Frame>(Channel.UNLIMITED)
    private val fileQueue = Channel<Frame>(64)
    private val closed = AtomicBoolean(false)
    private val eventsMutable = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SessionEvent> = eventsMutable
    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private var pingJob: Job? = null
    @Volatile
    private var lastRxAt = System.currentTimeMillis()

    fun start() {
        socket.soTimeout = 0
        socket.tcpNoDelay = true
        readerJob = scope.launch(Dispatchers.IO) { readLoop() }
        writerJob = scope.launch(Dispatchers.IO) { writeLoop() }
        pingJob = scope.launch { pingLoop() }
    }

    suspend fun send(frame: Frame) {
        if (closed.get()) throw SocketException("Sesión cerrada")
        if (frame.priority == FramePriority.FILE) {
            fileQueue.send(frame)
        } else {
            controlQueue.send(frame)
        }
    }

    suspend fun sendClose() {
        runCatching {
            withTimeout(limits.closeNotifyTimeoutMs) {
                send(Frame(FrameType.CLOSE, ByteArray(0)))
            }
        }
    }

    fun closeQuietly() {
        if (!closed.compareAndSet(false, true)) return
        controlQueue.close()
        fileQueue.close()
        readerJob?.cancel()
        writerJob?.cancel()
        pingJob?.cancel()
        runCatching { socket.close() }
    }

    private suspend fun writeLoop() {
        val out = socket.outputStream
        try {
            while (scope.isActive && !closed.get()) {
                val frame = nextOutgoing() ?: break
                withContext(Dispatchers.IO) {
                    Framing.write(out, frame, limits.maxFrameBytes)
                }
            }
        } catch (e: Exception) {
            emitDisconnected(e)
        }
    }

    private suspend fun nextOutgoing(): Frame? {
        val control = controlQueue.tryReceive().getOrNull()
        if (control != null) return control
        val file = fileQueue.tryReceive().getOrNull()
        if (file != null) return file
        val selected = kotlinx.coroutines.selects.select<Frame?> {
            controlQueue.onReceiveCatching { it.getOrNull() }
            fileQueue.onReceiveCatching { it.getOrNull() }
        }
        return selected
    }

    private suspend fun readLoop() {
        val input = socket.inputStream
        try {
            while (scope.isActive && !closed.get()) {
                val frame = withContext(Dispatchers.IO) {
                    Framing.read(input, limits.maxFrameBytes)
                }
                lastRxAt = System.currentTimeMillis()
                when (frame.type) {
                    FrameType.PING -> send(Frame(FrameType.PONG, frame.payload))
                    FrameType.PONG -> Unit
                    else -> eventsMutable.emit(SessionEvent.FrameIn(frame))
                }
            }
        } catch (e: Exception) {
            emitDisconnected(e)
        }
    }

    private suspend fun pingLoop() {
        while (scope.isActive && !closed.get()) {
            delay(limits.pingIntervalMs)
            val idle = System.currentTimeMillis() - lastRxAt
            if (idle > limits.idleTimeoutMs) {
                emitDisconnected(ProtocolException("Tiempo de espera agotado: el compañero no responde."))
                closeQuietly()
                return
            }
            runCatching { send(Frame(FrameType.PING, ByteArray(0))) }
        }
    }

    private suspend fun emitDisconnected(cause: Exception) {
        if (closed.get()) return
        eventsMutable.emit(SessionEvent.Disconnected(cause.message ?: "Conexión interrumpida"))
        closeQuietly()
    }
}

sealed interface SessionEvent {
    data class FrameIn(val frame: Frame) : SessionEvent
    data class Disconnected(val message: String) : SessionEvent
}
