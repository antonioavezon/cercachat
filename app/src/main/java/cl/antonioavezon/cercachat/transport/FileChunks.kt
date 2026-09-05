package cl.antonioavezon.cercachat.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object FileChunks {
    fun encode(id: String, offset: Long, data: ByteArray): ByteArray {
        val idBytes = id.toByteArray(StandardCharsets.US_ASCII)
        require(idBytes.size in 1..80)
        val buf = ByteBuffer.allocate(1 + idBytes.size + 8 + data.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(idBytes.size.toByte())
        buf.put(idBytes)
        buf.putLong(offset)
        buf.put(data)
        return buf.array()
    }

    fun decode(payload: ByteArray): Triple<String, Long, ByteArray> {
        require(payload.isNotEmpty()) { "Bloque vacío" }
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val idLen = buf.get().toInt() and 0xFF
        require(idLen in 1..80 && payload.size >= 1 + idLen + 8) { "Bloque corrupto" }
        val idBytes = ByteArray(idLen)
        buf.get(idBytes)
        val offset = buf.long
        val data = ByteArray(buf.remaining())
        buf.get(data)
        return Triple(String(idBytes, StandardCharsets.US_ASCII), offset, data)
    }
}
