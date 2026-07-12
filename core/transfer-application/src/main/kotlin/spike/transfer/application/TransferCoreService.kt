package spike.transfer.application

import spike.storage.transfer.ChunkStore
import spike.storage.transfer.MetadataStore
import spike.storage.transfer.ObjectStore
import spike.storage.transfer.TransactionManager
import spike.transfer.contracts.ChunkInput
import spike.transfer.contracts.ChunkReadHandle
import spike.transfer.contracts.CommandDisposition
import spike.transfer.contracts.ObjectSource
import spike.transfer.contracts.TransferCommandResult
import spike.transfer.contracts.TransferCore
import spike.transfer.contracts.TransferEventListener
import spike.transfer.contracts.TransferSnapshot
import spike.transfer.domain.ChunkIndex
import spike.transfer.domain.ManifestValidator
import spike.transfer.domain.MissingChunkCalculator
import spike.transfer.domain.MissingChunkSet
import spike.transfer.domain.NodeId
import spike.transfer.domain.ObjectId
import spike.transfer.domain.ObjectManifest
import spike.transfer.domain.Transfer
import spike.transfer.domain.TransferId
import spike.transfer.domain.TransferStateMachine

class TransferCoreService(
    private val metadataStore: MetadataStore,
    private val chunkStore: ChunkStore,
    private val objectStore: ObjectStore,
    private val transactionManager: TransactionManager,
    private val clock: Clock
) : TransferCore {
    private val listeners = mutableListOf<TransferEventListener>()
    private val manifestValidator = ManifestValidator()
    private val missingChunkCalculator = MissingChunkCalculator()
    private val stateMachine = TransferStateMachine()

    override suspend fun prepareOutboundObject(source: ObjectSource): TransferCommandResult<ObjectManifest> =
        rejected("Object preparation engine is not wired yet")

    override suspend fun createOutboundTransfer(
        objectId: ObjectId,
        remoteNodeId: NodeId
    ): TransferCommandResult<Transfer> =
        rejected("Outbound transfer creation is not wired yet")

    override suspend fun acceptInboundManifest(
        remoteNodeId: NodeId,
        manifest: ObjectManifest
    ): TransferCommandResult<Transfer> {
        return runCatching {
            manifestValidator.validate(manifest)
            transactionManager.transaction {
                metadataStore.saveManifest(manifest)
            }
            rejected<Transfer>("Inbound transfer creation is not wired yet")
        }.getOrElse { error ->
            TransferCommandResult(CommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }
    }

    override suspend fun activateTransfer(transferId: TransferId): TransferCommandResult<TransferSnapshot> =
        rejected("Activation is not wired yet")

    override suspend fun acceptChunk(input: ChunkInput): TransferCommandResult<TransferSnapshot> =
        rejected("Chunk verification pipeline is not wired yet")

    override suspend fun getTransfer(transferId: TransferId): Transfer? =
        metadataStore.getTransfer(transferId)

    override suspend fun getTransferSnapshot(transferId: TransferId): TransferSnapshot {
        val transfer = metadataStore.getTransfer(transferId)
            ?: throw spike.transfer.domain.TransferNotFound(transferId)
        return snapshot(transfer, null)
    }

    override suspend fun getMissingChunks(transferId: TransferId): MissingChunkSet {
        val transfer = metadataStore.getTransfer(transferId)
            ?: throw spike.transfer.domain.TransferNotFound(transferId)
        val manifest = metadataStore.getManifest(transfer.objectId)
            ?: throw spike.transfer.domain.InvalidManifest("Manifest not found for transfer")
        val verified = metadataStore.listVerifiedChunks(transfer.objectId).map { it.chunkIndex }.toSet()
        return missingChunkCalculator.calculate(manifest, verified, clock.nowEpochMillis())
    }

    override suspend fun openChunk(objectId: ObjectId, chunkIndex: ChunkIndex): ChunkReadHandle {
        val manifest = metadataStore.getManifest(objectId)
            ?: throw spike.transfer.domain.InvalidManifest("Manifest not found for object")
        val descriptor = manifest.chunks.getOrNull(chunkIndex.value)
            ?: throw spike.transfer.domain.InvalidChunkIndex(chunkIndex.value)
        return ChunkReadHandle(
            objectId = objectId,
            chunkIndex = chunkIndex,
            length = descriptor.length.value,
            content = chunkStore.openVerifiedRead(objectId, chunkIndex)
        )
    }

    override suspend fun interruptTransfer(
        transferId: TransferId,
        reason: String
    ): TransferCommandResult<TransferSnapshot> =
        rejected("Interruption transition is not wired yet")

    override suspend fun markWaiting(transferId: TransferId): TransferCommandResult<TransferSnapshot> =
        rejected("Waiting transition is not wired yet")

    override suspend fun resumeTransfer(transferId: TransferId): TransferCommandResult<TransferSnapshot> =
        rejected("Resume transition is not wired yet")

    override suspend fun verifyAndComplete(transferId: TransferId): TransferCommandResult<TransferSnapshot> =
        rejected("Final verification is not wired yet")

    override suspend fun cancelTransfer(transferId: TransferId): TransferCommandResult<TransferSnapshot> =
        rejected("Cancel transition is not wired yet")

    override suspend fun recover(): TransferCommandResult<List<TransferSnapshot>> =
        rejected("Recovery service is not wired yet")

    override fun subscribeEvents(listener: TransferEventListener) {
        listeners += listener
    }

    private fun snapshot(transfer: Transfer, missing: MissingChunkSet?): TransferSnapshot {
        val progress = if (transfer.totalBytes == 0L) 0.0 else transfer.verifiedBytes.toDouble() / transfer.totalBytes
        return TransferSnapshot(
            transferId = transfer.transferId,
            objectId = transfer.objectId,
            direction = transfer.direction,
            state = transfer.state,
            totalBytes = transfer.totalBytes,
            verifiedBytes = transfer.verifiedBytes,
            totalChunks = transfer.totalChunks,
            verifiedChunks = transfer.verifiedChunks,
            missingChunks = missing,
            progressRatio = progress,
            createdAtEpochMillis = transfer.createdAtEpochMillis,
            updatedAtEpochMillis = transfer.updatedAtEpochMillis,
            interruptedAtEpochMillis = transfer.interruptedAtEpochMillis,
            completedAtEpochMillis = transfer.completedAtEpochMillis,
            failureReason = transfer.failureReason
        )
    }

    private fun <T> rejected(errorCode: String): TransferCommandResult<T> =
        TransferCommandResult(CommandDisposition.REJECTED, null, errorCode)

    interface Clock {
        fun nowEpochMillis(): Long
    }
}
