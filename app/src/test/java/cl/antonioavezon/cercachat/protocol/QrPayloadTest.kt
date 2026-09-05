package cl.antonioavezon.cercachat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {
    private val now = 1_700_000_000_000L

    private fun sample(exp: Long = now + 60_000): QrPayload = QrPayload(
        protocolVersion = Protocol.VERSION,
        sessionId = "11111111-2222-3333-4444-555555555555",
        token = "abcdefghijklmnopqrstuvwx",
        fingerprintSha256 = "a".repeat(64),
        ssid = "AndroidShare_1234",
        psk = "abcdefgh",
        host = "192.168.49.1",
        port = Protocol.DEFAULT_PORT,
        expiresAtMs = exp,
    )

    @Test
    fun roundTrip() {
        val encoded = sample().encode()
        val decoded = QrPayload.decode(encoded, now)
        assertTrue(decoded is QrDecodeResult.Success)
        val payload = (decoded as QrDecodeResult.Success).payload
        assertEquals("192.168.49.1", payload.host)
        assertEquals(Protocol.DEFAULT_PORT, payload.port)
    }

    @Test
    fun rejectsExpired() {
        val encoded = sample(exp = now - 1).encode()
        val decoded = QrPayload.decode(encoded, now)
        assertTrue(decoded is QrDecodeResult.Invalid)
        assertTrue((decoded as QrDecodeResult.Invalid).message.contains("caduc"))
    }

    @Test
    fun rejectsWrongPrefix() {
        val decoded = QrPayload.decode("https://example.com", now)
        assertTrue(decoded is QrDecodeResult.Invalid)
    }

    @Test
    fun rejectsOversized() {
        val huge = "cercachat:1:" + "A".repeat(5000)
        val decoded = QrPayload.decode(huge, now)
        assertTrue(decoded is QrDecodeResult.Invalid)
    }

    @Test
    fun rejectsWrongVersion() {
        val payload = sample().copy(protocolVersion = 99)
        val result = payload.validate(now)
        assertTrue(result is QrValidation.Error)
    }
}
