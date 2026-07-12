package spike.secure.session.domain

data class SecureSessionSnapshot(
    val sessionId: String,
    val encounterId: String,
    val localNodeId: String,
    val remoteNodeId: String,
    val role: SessionRole?,
    val protocolVersion: Int,
    val suiteId: String,
    val state: SessionState,
    val keyEpoch: Int,
    val recordsSent: Long,
    val recordsReceived: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val createdAt: Long,
    val establishedAt: Long?,
    val lastActivityAt: Long,
    val closedAt: Long?,
    val failureReason: String?
)
