package cl.antonioavezon.cercachat.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FramesTest {
    @Test
    fun writeAndReadTextFrame() {
        val original = Frame(FrameType.TEXT, "hola".toByteArray())
        val out = ByteArrayOutputStream()
        Framing.write(out, original, 64 * 1024)
        val read = Framing.read(ByteArrayInputStream(out.toByteArray()), 64 * 1024)
        assertEquals(original, read)
    }

    @Test
    fun rejectsOversizeFrame() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 4))
        assertThrows(ProtocolException::class.java) {
            Framing.read(ByteArrayInputStream(out.toByteArray()), 16)
        }
    }

    @Test
    fun interleavingTypesStayIntact() {
        val out = ByteArrayOutputStream()
        Framing.write(out, Frame(FrameType.TEXT, "a".toByteArray()), 1024)
        Framing.write(out, Frame(FrameType.FILE_CHUNK, byteArrayOf(1, 2, 3)), 1024)
        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals(FrameType.TEXT, Framing.read(input, 1024).type)
        assertEquals(FrameType.FILE_CHUNK, Framing.read(input, 1024).type)
    }
}
