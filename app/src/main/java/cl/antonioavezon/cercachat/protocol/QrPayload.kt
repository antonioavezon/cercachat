package cl.antonioavezon.cercachat.protocol

import java.nio.charset.StandardCharsets
import java.util.Base64

data class QrPayload(
    val protocolVersion: Int,
    val sessionId: String,
    val token: String,
    val fingerprintSha256: String,
    val ssid: String,
    val psk: String,
    val host: String,
    val port: Int,
    val expiresAtMs: Long,
) {
    fun encode(): String {
        val json = FlatJson.obj(
            "v" to protocolVersion,
            "sid" to sessionId,
            "tok" to token,
            "fp" to fingerprintSha256,
            "ssid" to ssid,
            "psk" to psk,
            "host" to host,
            "port" to port,
            "exp" to expiresAtMs,
        )
        val b64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        return Protocol.PREFIX + b64
    }

    fun validate(nowMs: Long, limits: TransferLimits = TransferLimits()): QrValidation {
        if (protocolVersion != Protocol.VERSION) {
            return QrValidation.Error("Versión de protocolo no compatible (v$protocolVersion).")
        }
        if (sessionId.isBlank() || sessionId.length > 64) {
            return QrValidation.Error("Identificador de sesión inválido.")
        }
        if (!TOKEN_REGEX.matches(token)) {
            return QrValidation.Error("Token de emparejamiento inválido.")
        }
        if (!FP_REGEX.matches(fingerprintSha256)) {
            return QrValidation.Error("Huella criptográfica inválida.")
        }
        if (ssid.isBlank() || ssid.length > 32) {
            return QrValidation.Error("SSID inválido.")
        }
        if (psk.length !in 8..63) {
            return QrValidation.Error("Credencial de red inválida.")
        }
        if (!IPV4_REGEX.matches(host) || isReservedOrNonUnicast(host)) {
            return QrValidation.Error("Dirección del anfitrión inválida.")
        }
        if (port !in 1024..65535) {
            return QrValidation.Error("Puerto inválido.")
        }
        if (nowMs > expiresAtMs) {
            return QrValidation.Error("El código QR caducó. Pide uno nuevo.")
        }
        val encoded = encode()
        if (encoded.length > limits.maxQrChars) {
            return QrValidation.Error("El código QR es demasiado grande.")
        }
        return QrValidation.Ok
    }

    companion object {
        private val TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{16,86}$")
        private val FP_REGEX = Regex("^[a-f0-9]{64}$")
        private val IPV4_REGEX = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")

        fun decode(raw: String, nowMs: Long, limits: TransferLimits = TransferLimits()): QrDecodeResult {
            val text = raw.trim()
            if (text.length > limits.maxQrChars) {
                return QrDecodeResult.Invalid("El código QR es demasiado grande.")
            }
            if (!text.startsWith(Protocol.PREFIX)) {
                return QrDecodeResult.Invalid("El código no pertenece a CercaChat.")
            }
            val body = text.removePrefix(Protocol.PREFIX)
            val json = try {
                String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                return QrDecodeResult.Invalid("El código QR está dañado.")
            }
            val map = try {
                FlatJson.parse(json)
            } catch (_: Exception) {
                return QrDecodeResult.Invalid("El formato del QR no es válido.")
            }
            val payload = try {
                QrPayload(
                    protocolVersion = FlatJson.int(map, "v"),
                    sessionId = FlatJson.string(map, "sid"),
                    token = FlatJson.string(map, "tok"),
                    fingerprintSha256 = FlatJson.string(map, "fp").lowercase(),
                    ssid = FlatJson.string(map, "ssid"),
                    psk = FlatJson.string(map, "psk"),
                    host = FlatJson.string(map, "host"),
                    port = FlatJson.int(map, "port"),
                    expiresAtMs = FlatJson.long(map, "exp"),
                )
            } catch (_: Exception) {
                return QrDecodeResult.Invalid("Faltan datos de conexión en el QR.")
            }
            return when (val validation = payload.validate(nowMs, limits)) {
                QrValidation.Ok -> QrDecodeResult.Success(payload)
                is QrValidation.Error -> QrDecodeResult.Invalid(validation.message)
            }
        }

        private fun isReservedOrNonUnicast(host: String): Boolean {
            val parts = host.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return true
            val (a, b, c, d) = parts
            if (a == 0 || a == 127 || a >= 224) return true
            if (a == 169 && b == 254) return true
            if (d == 0 || d == 255) return true
            return false
        }
    }
}

sealed interface QrValidation {
    data object Ok : QrValidation
    data class Error(val message: String) : QrValidation
}

sealed interface QrDecodeResult {
    data class Success(val payload: QrPayload) : QrDecodeResult
    data class Invalid(val message: String) : QrDecodeResult
}
