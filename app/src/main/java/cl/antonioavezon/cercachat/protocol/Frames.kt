package cl.antonioavezon.cercachat.protocol

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class FrameType(val code: Int) {
    AUTH(1),
    AUTH_OK(2),
    AUTH_FAIL(3),
    TEXT(4),
    TEXT_ACK(5),
    FILE_META(6),
    FILE_CHUNK(7),
    FILE_END(8),
    FILE_ACK(9),
    FILE_NACK(10),
    FILE_CANCEL(11),
    PING(12),
    PONG(13),
    CLOSE(14),
    ERROR(15);

    companion object {
        fun from(code: Int): FrameType? = entries.firstOrNull { it.code == code }
    }
}

enum class FramePriority { CONTROL, FILE }

data class Frame(
    val type: FrameType,
    val payload: ByteArray,
    val priority: FramePriority = if (type == FrameType.FILE_CHUNK) FramePriority.FILE else FramePriority.CONTROL,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return type == other.type && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * type.hashCode() + payload.contentHashCode()
}

object Framing {
    fun write(out: OutputStream, frame: Frame, maxFrameBytes: Int) {
        val length = 1 + frame.payload.size
        require(length in 1..maxFrameBytes) { "Marco fuera de límite: $length" }
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array()
        out.write(header)
        out.write(frame.type.code)
        out.write(frame.payload)
        out.flush()
    }

    fun read(input: InputStream, maxFrameBytes: Int): Frame {
        val header = readFully(input, 4)
        val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (length < 1 || length > maxFrameBytes) {
            throw ProtocolException("Tamaño de marco inválido: $length")
        }
        val body = readFully(input, length)
        val type = FrameType.from(body[0].toInt() and 0xFF)
            ?: throw ProtocolException("Tipo de marco desconocido")
        val payload = if (body.size == 1) ByteArray(0) else body.copyOfRange(1, body.size)
        return Frame(type, payload)
    }

    fun readFully(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var off = 0
        while (off < count) {
            val n = input.read(buf, off, count - off)
            if (n < 0) throw EOFException("Conexión cerrada al leer el marco")
            off += n
        }
        return buf
    }
}

class ProtocolException(message: String) : Exception(message)
