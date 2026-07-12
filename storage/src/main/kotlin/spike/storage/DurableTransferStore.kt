package spike.storage

import spike.transfer.ChunkDescriptor
import spike.transfer.ObjectManifest
import spike.transfer.TransferRecord

interface DurableTransferStore {
    suspend fun saveManifest(manifest: ObjectManifest)

    suspend fun loadManifest(objectId: String): ObjectManifest?

    suspend fun saveTransfer(record: TransferRecord)

    suspend fun loadIncompleteTransfers(): List<TransferRecord>

    suspend fun persistVerifiedChunk(descriptor: ChunkDescriptor, payload: ByteArray)

    suspend fun verifiedChunkIndexes(objectId: String): Set<Int>

    suspend fun markCompleted(transferId: String, completedAtEpochMillis: Long)
}
