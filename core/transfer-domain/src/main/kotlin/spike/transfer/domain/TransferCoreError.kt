package spike.transfer.domain

sealed class TransferCoreError(
    val code: String,
    message: String
) : Exception(message)

sealed class ValidationError(code: String, message: String) : TransferCoreError(code, message)
class InvalidObjectId(message: String) : ValidationError("InvalidObjectId", message)
class InvalidTransferId(message: String) : ValidationError("InvalidTransferId", message)
class InvalidManifest(message: String) : ValidationError("InvalidManifest", message)
class UnsupportedManifestVersion(version: Int) :
    ValidationError("UnsupportedManifestVersion", "Unsupported manifest version: $version")
class InvalidChunkIndex(index: Int) :
    ValidationError("InvalidChunkIndex", "Invalid chunk index: $index")
class InvalidChunkLength(length: Long) :
    ValidationError("InvalidChunkLength", "Invalid chunk length: $length")

sealed class IntegrityError(code: String, message: String) : TransferCoreError(code, message)
class ChunkHashMismatch(index: Int) :
    IntegrityError("ChunkHashMismatch", "Chunk hash mismatch at index: $index")
class ObjectHashMismatch(objectId: ObjectId) :
    IntegrityError("ObjectHashMismatch", "Object hash mismatch for object: ${objectId.value}")
class StoredChunkCorruption(index: Int) :
    IntegrityError("StoredChunkCorruption", "Stored chunk corruption at index: $index")

sealed class StateError(code: String, message: String) : TransferCoreError(code, message)
class TransferNotFound(transferId: TransferId) :
    StateError("TransferNotFound", "Transfer not found: ${transferId.value}")
class InvalidTransferStateTransition(from: TransferState, to: TransferState) :
    StateError("InvalidTransferStateTransition", "Invalid transfer state transition: $from -> $to")
class TransferAlreadyTerminal(transferId: TransferId) :
    StateError("TransferAlreadyTerminal", "Transfer is already terminal: ${transferId.value}")
class TransferDirectionMismatch(transferId: TransferId) :
    StateError("TransferDirectionMismatch", "Transfer direction mismatch: ${transferId.value}")

sealed class StorageError(code: String, message: String) : TransferCoreError(code, message)
class InsufficientStorage(message: String) : StorageError("InsufficientStorage", message)
class ChunkWriteFailure(message: String) : StorageError("ChunkWriteFailure", message)
class MetadataCommitFailure(message: String) : StorageError("MetadataCommitFailure", message)
class ObjectFinalizationFailure(message: String) : StorageError("ObjectFinalizationFailure", message)
class StorageInconsistency(message: String) : StorageError("StorageInconsistency", message)

sealed class ConcurrencyError(code: String, message: String) : TransferCoreError(code, message)
class ConcurrentMutationRejected(transferId: TransferId) :
    ConcurrencyError("ConcurrentMutationRejected", "Concurrent mutation rejected: ${transferId.value}")
class OptimisticLockConflict(transferId: TransferId) :
    ConcurrencyError("OptimisticLockConflict", "Optimistic lock conflict: ${transferId.value}")

sealed class RecoveryError(code: String, message: String) : TransferCoreError(code, message)
class RecoveryInvariantViolation(message: String) : RecoveryError("RecoveryInvariantViolation", message)
class UnrecoverableTransferState(transferId: TransferId) :
    RecoveryError("UnrecoverableTransferState", "Unrecoverable transfer state: ${transferId.value}")
