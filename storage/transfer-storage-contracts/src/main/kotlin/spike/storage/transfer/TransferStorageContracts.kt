package spike.storage.transfer

import spike.transfer.contracts.BoundedReadableStream
import spike.transfer.domain.ChunkIndex
import spike.transfer.domain.ObjectId
import spike.transfer.domain.ObjectManifest
import spike.transfer.domain.Transfer
import spike.transfer.domain.TransferEvent
import spike.transfer.domain.TransferId
import spike.transfer.domain.VerifiedChunk

interface TransactionManager {
    suspend fun <T> transaction(block: suspend () -> T): T
}

interface MetadataStore {
    suspend fun createTransfer(transfer: Transfer)
    suspend fun getTransfer(transferId: TransferId): Transfer?
    suspend fun updateTransfer(transfer: Transfer)
    suspend fun saveManifest(manifest: ObjectManifest)
    suspend fun getManifest(objectId: ObjectId): ObjectManifest?
    suspend fun registerVerifiedChunk(chunk: VerifiedChunk)
    suspend fun removeVerifiedChunk(objectId: ObjectId, chunkIndex: ChunkIndex)
    suspend fun hasVerifiedChunk(objectId: ObjectId, chunkIndex: ChunkIndex): Boolean
    suspend fun listVerifiedChunks(objectId: ObjectId): List<VerifiedChunk>
    suspend fun appendEvent(event: TransferEvent)
    suspend fun listEvents(transferId: TransferId): List<TransferEvent>
    suspend fun nextEventSequence(transferId: TransferId): Long
    suspend fun findRecoverableTransfers(): List<Transfer>
    suspend fun markCompleted(transferId: TransferId, completedAtEpochMillis: Long)
    suspend fun markFailed(transferId: TransferId, reason: String)
}

interface ChunkStore {
    suspend fun openTemporaryWrite(objectId: ObjectId, chunkIndex: ChunkIndex): ChunkWriteSession
    suspend fun openVerifiedRead(objectId: ObjectId, chunkIndex: ChunkIndex): BoundedReadableStream
    suspend fun verifiedChunkExists(storageReference: String): Boolean
    suspend fun deleteTemporaryChunks()
    suspend fun deleteOrQuarantineUnregisteredChunk(storageReference: String)
}

interface ChunkWriteSession {
    val storageReference: String
    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
    suspend fun commitVerified(): String
    suspend fun abort()
}

interface ObjectStore {
    suspend fun rememberLocalSource(objectId: ObjectId, stableSourceId: String)
    suspend fun openLocalSource(objectId: ObjectId): BoundedReadableStream
    suspend fun openPartialObjectWrite(objectId: ObjectId): ObjectWriteSession
    suspend fun openCompletedObject(objectId: ObjectId): BoundedReadableStream
}

interface ObjectWriteSession {
    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
    suspend fun flushDurably()
    suspend fun commitComplete()
    suspend fun abort()
}
