package spike.transfer

const val DEFAULT_CHUNK_SIZE_BYTES: Long = 1024L * 1024L
const val MANIFEST_VERSION: Int = 1

enum class TransferState {
    CREATED,
    DISCOVERING,
    PEER_FOUND,
    NEGOTIATING,
    UPGRADING_TRANSPORT,
    TRANSFERRING,
    INTERRUPTED,
    WAITING_FOR_PEER,
    RESUMING,
    VERIFYING,
    COMPLETED,
    FAILED
}

enum class TransportKind {
    BLE,
    WIFI_DIRECT,
    NONE
}

data class Node(
    val localNodeId: String,
    val protocolVersion: Int,
    val capabilities: PeerCapabilities,
    val sessionNonce: String
)

data class Peer(
    val peerId: String,
    val protocolVersion: Int,
    val capabilities: PeerCapabilities,
    val bleAddressOrHandle: String,
    val lastSeenAtEpochMillis: Long,
    val connectionState: String
)

data class PeerCapabilities(
    val protocolVersion: Int,
    val supportsBleControl: Boolean,
    val supportsWifiDirect: Boolean,
    val maxChunkSize: Long,
    val maxParallelStreams: Int,
    val availableStorageClass: String
)

data class TransferObject(
    val objectId: String,
    val totalSize: Long,
    val chunkSize: Long,
    val chunkCount: Int,
    val objectHashHex: String,
    val manifestVersion: Int = MANIFEST_VERSION
)

data class ChunkDescriptor(
    val objectId: String,
    val chunkIndex: Int,
    val offset: Long,
    val length: Int,
    val chunkHashHex: String
)

data class ObjectManifest(
    val manifestVersion: Int,
    val transferObject: TransferObject,
    val chunks: List<ChunkDescriptor>
) {
    init {
        require(manifestVersion == MANIFEST_VERSION) { "Unsupported manifest version: $manifestVersion" }
        require(chunks.size == transferObject.chunkCount) { "Chunk count mismatch" }
    }
}

data class TransferRecord(
    val transferId: String,
    val objectId: String,
    val senderNodeId: String,
    val receiverNodeId: String,
    val state: TransferState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val bytesVerified: Long,
    val chunksVerified: Int
)
