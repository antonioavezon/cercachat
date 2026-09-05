package cl.antonioavezon.cercachat.protocol

data class TransferLimits(
    val maxFileBytes: Long = 100L * 1024L * 1024L,
    val chunkBytes: Int = 32 * 1024,
    val maxFrameBytes: Int = 64 * 1024,
    val maxTextChars: Int = 4_000,
    val maxAliasChars: Int = 40,
    val maxQrChars: Int = 1_400,
    val qrTtlMs: Long = 3L * 60L * 1_000L,
    val connectTimeoutMs: Int = 20_000,
    val handshakeTimeoutMs: Int = 15_000,
    val idleTimeoutMs: Long = 45_000L,
    val pingIntervalMs: Long = 12_000L,
    val closeNotifyTimeoutMs: Long = 2_000L,
) {
    companion object {
        fun fromMaxFileMb(maxFileMb: Int): TransferLimits {
            val bounded = maxFileMb.coerceIn(1, 500)
            return TransferLimits(maxFileBytes = bounded.toLong() * 1024L * 1024L)
        }
    }
}

object Protocol {
    const val VERSION: Int = 1
    const val PREFIX: String = "cercachat:1:"
    const val DEFAULT_PORT: Int = 41765
}
