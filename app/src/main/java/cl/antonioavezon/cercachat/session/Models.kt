package cl.antonioavezon.cercachat.session

enum class ConnectionPhase {
    Idle,
    Preparing,
    WaitingPeer,
    Connecting,
    Connected,
    Error,
}

enum class DeliveryStatus { SENDING, DELIVERED, ERROR }

enum class LineKind { TEXT, FILE, VOICE }

enum class SessionRole { HOST, CLIENT }

data class ChatLine(
    val id: String,
    val kind: LineKind,
    val mine: Boolean,
    val text: String = "",
    val sentAtMs: Long,
    val status: DeliveryStatus = DeliveryStatus.SENDING,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val progress: Float = 0f,
    val localPath: String? = null,
    val durationMs: Long? = null,
    val error: String? = null,
    val transferActive: Boolean = false,
)

data class RetentionPrompt(
    val sessionId: String,
    val fileCount: Int,
    val totalBytes: Long,
)

data class SessionSnapshot(
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val role: SessionRole? = null,
    val statusText: String = "Sin conexión",
    val errorMessage: String? = null,
    val qrContent: String? = null,
    val qrExpiresAtMs: Long? = null,
    val hostIp: String? = null,
    val localAlias: String = "",
    val peerAlias: String = "",
    val messages: List<ChatLine> = emptyList(),
    val connected: Boolean = false,
    val retention: RetentionPrompt? = null,
)
