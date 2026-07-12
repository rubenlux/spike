package spike.transfer.domain

const val SUPPORTED_MANIFEST_VERSION = 1

enum class TransferDirection {
    OUTBOUND,
    INBOUND
}

enum class TransferState {
    CREATED,
    PREPARING,
    READY,
    ACTIVE,
    INTERRUPTED,
    WAITING,
    RESUMING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TransferEventType {
    TRANSFER_CREATED,
    OBJECT_PREPARATION_STARTED,
    OBJECT_PREPARED,
    MANIFEST_CREATED,
    MANIFEST_ACCEPTED,
    TRANSFER_READY,
    TRANSFER_ACTIVATED,
    CHUNK_RECEIVE_STARTED,
    CHUNK_RECEIVED,
    CHUNK_VERIFIED,
    CHUNK_REJECTED,
    TRANSFER_INTERRUPTED,
    TRANSFER_WAITING,
    TRANSFER_RESUME_STARTED,
    TRANSFER_RESUMED,
    MISSING_CHUNKS_CALCULATED,
    OBJECT_RECONSTRUCTION_STARTED,
    OBJECT_RECONSTRUCTED,
    OBJECT_VERIFICATION_STARTED,
    OBJECT_VERIFIED,
    TRANSFER_COMPLETED,
    TRANSFER_FAILED,
    TRANSFER_CANCELLED,
    TRANSFER_RECOVERED
}

data class TransferObject(
    val objectId: ObjectId,
    val totalSize: ByteLength,
    val objectHash: Sha256Hash,
    val createdAtEpochMillis: Long
) {
    init {
        require(objectId == objectHash.toObjectId()) { "ObjectId must match object hash" }
    }
}

data class ChunkDescriptor(
    val chunkIndex: ChunkIndex,
    val offset: ByteOffset,
    val length: ByteLength,
    val chunkHash: Sha256Hash
)

data class ObjectManifest(
    val manifestVersion: ManifestVersion,
    val objectId: ObjectId,
    val totalSize: ByteLength,
    val chunkSize: ByteLength,
    val chunkCount: Int,
    val objectHash: Sha256Hash,
    val chunks: List<ChunkDescriptor>,
    val createdAtEpochMillis: Long
)

data class Transfer(
    val transferId: TransferId,
    val objectId: ObjectId,
    val direction: TransferDirection,
    val localNodeId: NodeId,
    val remoteNodeId: NodeId,
    val state: TransferState,
    val totalBytes: Long,
    val verifiedBytes: Long,
    val totalChunks: Int,
    val verifiedChunks: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val interruptedAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
    val failureReason: String?
)

data class VerifiedChunk(
    val objectId: ObjectId,
    val chunkIndex: ChunkIndex,
    val chunkHash: Sha256Hash,
    val length: ByteLength,
    val storageReference: String,
    val verifiedAtEpochMillis: Long
)

data class TransferEvent(
    val eventId: String,
    val transferId: TransferId,
    val eventType: TransferEventType,
    val sequenceNumber: Long,
    val occurredAtEpochMillis: Long,
    val metadata: Map<String, String>
)
