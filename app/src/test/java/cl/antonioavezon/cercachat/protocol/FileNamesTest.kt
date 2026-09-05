package cl.antonioavezon.cercachat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test
    fun stripsPathTraversal() {
        val name = FileNames.sanitize("../../etc/passwd")
        assertFalse(name.contains(".."))
        assertFalse(name.contains("/"))
    }

    @Test
    fun uniqueSuffix() {
        val existing = setOf("foto.jpg")
        val next = FileNames.unique("foto.jpg", existing)
        assertEquals("foto (2).jpg", next)
    }

    @Test
    fun emptyBecomesArchivo() {
        assertEquals("archivo", FileNames.sanitize("   "))
    }

    @Test
    fun looksLikeTraversal() {
        assertTrue(FileNames.looksLikeTraversal("../x"))
        assertFalse(FileNames.looksLikeTraversal("nota.m4a"))
    }
}
