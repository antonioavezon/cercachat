package cl.antonioavezon.cercachat.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosePolicyTest {
    @Test
    fun promptsOnlyWhenFilesExist() {
        assertTrue(ClosePolicy.shouldPromptRetention(2))
        assertFalse(ClosePolicy.shouldPromptRetention(0))
    }

    @Test
    fun invalidatesQrOnClose() {
        assertNull(ClosePolicy.invalidateQr("cercachat:1:abc", true))
    }

    @Test
    fun tokenCannotBeReusedAfterUseOrClose() {
        assertFalse(ClosePolicy.canReuseToken(tokenUsed = true, closed = false))
        assertFalse(ClosePolicy.canReuseToken(tokenUsed = false, closed = true))
        assertTrue(ClosePolicy.canReuseToken(tokenUsed = false, closed = false))
    }

    @Test
    fun pendingRetentionAfterCrash() {
        assertTrue(ClosePolicy.pendingRetentionAfterCrash(hadManagedFiles = true, decisionTaken = false))
        assertFalse(ClosePolicy.pendingRetentionAfterCrash(hadManagedFiles = true, decisionTaken = true))
        assertFalse(ClosePolicy.pendingRetentionAfterCrash(hadManagedFiles = false, decisionTaken = false))
    }
}
