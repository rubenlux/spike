package spike.transfer.contracts

import spike.transfer.domain.ChunkIndex
import spike.transfer.domain.MissingChunkSet
import spike.transfer.domain.NodeId
import spike.transfer.domain.ObjectId
import spike.transfer.domain.ObjectManifest
import spike.transfer.domain.Transfer
import spike.transfer.domain.TransferDirection
import spike.transfer.domain.TransferEvent
import spike.transfer.domain.TransferId
import spike.transfer.domain.TransferState

interface BoundedReadableStream {
    val length: Long
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun close()
}

data class ObjectSource(
    val stableSourceId: String,
    val length: Long,
    val streamFactory: () -> BoundedReadableStream
)

data class ChunkInput(
    val transferId: TransferId,
    val objectId: ObjectId,
    val chunkIndex: ChunkIndex,
    val declaredLength: Long,
    val expectedHashHex: String,
    val content: BoundedReadableStream
)

data class ChunkReadHandle(
    val objectId: ObjectId,
    val chunkIndex: ChunkIndex,
    val length: Long,
    val content: BoundedReadableStream
)

data class TransferSnapshot(
    val transferId: TransferId,
    val objectId: ObjectId,
    val direction: TransferDirection,
    val state: TransferState,
    val totalBytes: Long,
    val verifiedBytes: Long,
    val totalChunks: Int,
    val verifiedChunks: Int,
    val missingChunks: MissingChunkSet?,
    val progressRatio: Double,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val interruptedAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
    val failureReason: String?
)

enum class CommandDisposition {
    APPLIED,
    ALREADY_APPLIED,
    REJECTED
}

data class TransferCommandResult<T>(
    val disposition: CommandDisposition,
    val value: T?,
    val errorCode: String?
)

interface TransferEventListener {
    fun onEvent(event: TransferEvent)
}

interface TransferCore {
    suspend fun prepareOutboundObject(source: ObjectSource): TransferCommandResult<ObjectManifest>
    suspend fun createOutboundTransfer(objectId: ObjectId, remoteNodeId: NodeId): TransferCommandResult<Transfer>
    suspend fun acceptInboundManifest(remoteNodeId: NodeId, manifest: ObjectManifest): TransferCommandResult<Transfer>
    suspend fun activateTransfer(transferId: TransferId): TransferCommandResult<TransferSnapshot>
    suspend fun acceptChunk(input: ChunkInput): TransferCommandResult<TransferSnapshot>
    suspend fun getTransfer(transferId: TransferId): Transfer?
    suspend fun getTransferSnapshot(transferId: TransferId): TransferSnapshot
    suspend fun getMissingChunks(transferId: TransferId): MissingChunkSet
    suspend fun openChunk(objectId: ObjectId, chunkIndex: ChunkIndex): ChunkReadHandle
    suspend fun interruptTransfer(transferId: TransferId, reason: String): TransferCommandResult<TransferSnapshot>
    suspend fun markWaiting(transferId: TransferId): TransferCommandResult<TransferSnapshot>
    suspend fun resumeTransfer(transferId: TransferId): TransferCommandResult<TransferSnapshot>
    suspend fun verifyAndComplete(transferId: TransferId): TransferCommandResult<TransferSnapshot>
    suspend fun cancelTransfer(transferId: TransferId): TransferCommandResult<TransferSnapshot>
    suspend fun recover(): TransferCommandResult<List<TransferSnapshot>>
    fun subscribeEvents(listener: TransferEventListener)
}
