package cl.antonioavezon.cercachat.transport

import android.net.Network
import cl.antonioavezon.cercachat.protocol.CryptoIds
import cl.antonioavezon.cercachat.protocol.FlatJson
import cl.antonioavezon.cercachat.protocol.Frame
import cl.antonioavezon.cercachat.protocol.FrameType
import cl.antonioavezon.cercachat.protocol.Framing
import cl.antonioavezon.cercachat.protocol.ProtocolException
import cl.antonioavezon.cercachat.protocol.QrPayload
import cl.antonioavezon.cercachat.protocol.TransferLimits
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class AuthenticatedLink(
    val socket: SSLSocket,
    val peerAlias: String,
    val sessionId: String,
)

object PairingTransport {
    suspend fun hostAccept(
        bindIpv4: String,
        port: Int,
        identity: HostIdentity,
        expectedToken: String,
        sessionId: String,
        localAlias: String,
        expiresAtMs: Long,
        tokenAlreadyUsed: () -> Boolean,
        markTokenUsed: () -> Unit,
        limits: TransferLimits,
    ): AuthenticatedLink = withContext(Dispatchers.IO) {
        val bind = runCatching { InetAddress.getByName(bindIpv4) }.getOrNull()
        val server = identity.sslContext.serverSocketFactory.createServerSocket() as SSLServerSocket
        server.needClientAuth = false
        server.reuseAddress = true
        server.soTimeout = limits.connectTimeoutMs
        try {
            val address = if (bind != null) InetSocketAddress(bind, port) else InetSocketAddress(port)
            runCatching { server.bind(address) }.getOrElse {
                server.bind(InetSocketAddress(port))
            }
            val raw = server.accept() as SSLSocket
            raw.soTimeout = limits.handshakeTimeoutMs
            raw.startHandshake()
            val auth = Framing.read(raw.inputStream, limits.maxFrameBytes)
            if (auth.type != FrameType.AUTH) {
                throw ProtocolException("El compañero no envió autenticación.")
            }
            val map = FlatJson.parse(auth.payload.toString(StandardCharsets.UTF_8))
            val token = FlatJson.string(map, "tok")
            val alias = FlatJson.optString(map, "alias").orEmpty().take(limits.maxAliasChars)
            if (System.currentTimeMillis() > expiresAtMs) {
                writeFail(raw, "QR_EXPIRED", limits)
                throw ProtocolException("El QR caducó antes de completar el emparejamiento.")
            }
            if (tokenAlreadyUsed() || !CryptoIds.constantTimeEquals(token, expectedToken)) {
                writeFail(raw, "TOKEN", limits)
                throw ProtocolException("El token no es válido o ya se usó.")
            }
            markTokenUsed()
            val ok = FlatJson.obj("alias" to localAlias, "sid" to sessionId)
            Framing.write(
                raw.outputStream,
                Frame(FrameType.AUTH_OK, ok.toByteArray(StandardCharsets.UTF_8)),
                limits.maxFrameBytes,
            )
            raw.soTimeout = 0
            AuthenticatedLink(raw, alias.ifBlank { "Compañero" }, sessionId)
        } finally {
            runCatching { server.close() }
        }
    }

    suspend fun clientConnect(
        network: Network,
        payload: QrPayload,
        localAlias: String,
        limits: TransferLimits,
    ): AuthenticatedLink = withContext(Dispatchers.IO) {
        val ssl = TlsFactory.createPinnedClientContext(payload.fingerprintSha256)
        val plain = network.socketFactory.createSocket()
        try {
            withTimeout(limits.connectTimeoutMs.toLong()) {
                plain.connect(InetSocketAddress(payload.host, payload.port), limits.connectTimeoutMs)
            }
        } catch (e: Exception) {
            runCatching { plain.close() }
            throw WifiClientException(
                "No se pudo abrir el socket hacia ${payload.host}:${payload.port}. ${e.message ?: ""}".trim()
            )
        }
        val sslSocket = ssl.socketFactory.createSocket(
            plain,
            payload.host,
            payload.port,
            true,
        ) as SSLSocket
        sslSocket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
        sslSocket.soTimeout = limits.handshakeTimeoutMs
        sslSocket.startHandshake()
        val auth = FlatJson.obj(
            "tok" to payload.token,
            "alias" to localAlias.take(limits.maxAliasChars),
            "cid" to CryptoIds.sessionId(),
        )
        Framing.write(
            sslSocket.outputStream,
            Frame(FrameType.AUTH, auth.toByteArray(StandardCharsets.UTF_8)),
            limits.maxFrameBytes,
        )
        val reply = Framing.read(sslSocket.inputStream, limits.maxFrameBytes)
        if (reply.type == FrameType.AUTH_FAIL) {
            sslSocket.close()
            throw ProtocolException("El anfitrión rechazó el emparejamiento. El QR puede haber caducado o ya se usó.")
        }
        if (reply.type != FrameType.AUTH_OK) {
            sslSocket.close()
            throw ProtocolException("Respuesta de autenticación inesperada.")
        }
        val map = FlatJson.parse(reply.payload.toString(StandardCharsets.UTF_8))
        sslSocket.soTimeout = 0
        AuthenticatedLink(
            socket = sslSocket,
            peerAlias = FlatJson.optString(map, "alias").orEmpty().ifBlank { "Anfitrión" },
            sessionId = FlatJson.optString(map, "sid") ?: payload.sessionId,
        )
    }

    private fun writeFail(socket: SSLSocket, code: String, limits: TransferLimits) {
        val payload = FlatJson.obj("code" to code).toByteArray(StandardCharsets.UTF_8)
        runCatching {
            Framing.write(socket.outputStream, Frame(FrameType.AUTH_FAIL, payload), limits.maxFrameBytes)
        }
        runCatching { socket.close() }
    }
}
