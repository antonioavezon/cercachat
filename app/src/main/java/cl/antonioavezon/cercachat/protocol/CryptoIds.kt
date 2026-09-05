package cl.antonioavezon.cercachat.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object CryptoIds {
    private val random = SecureRandom()

    fun sessionId(): String = UUID.randomUUID().toString()

    fun messageId(): String = UUID.randomUUID().toString()

    fun oneTimeToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return androidSafeBase64(bytes)
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray()
        val right = b.toByteArray()
        if (left.size != right.size) {
            var acc = left.size xor right.size
            left.forEach { acc = acc or it.toInt() }
            return false
        }
        var diff = 0
        for (i in left.indices) {
            diff = diff or (left[i].toInt() xor right[i].toInt())
        }
        return diff == 0
    }

    private fun androidSafeBase64(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            out.append(table[b0 shr 2])
            out.append(table[((b0 and 3) shl 4) or (b1 shr 4)])
            if (i + 1 < bytes.size) out.append(table[((b1 and 15) shl 2) or (b2 shr 6)])
            if (i + 2 < bytes.size) out.append(table[b2 and 63])
            i += 3
        }
        return out.toString()
    }
}
