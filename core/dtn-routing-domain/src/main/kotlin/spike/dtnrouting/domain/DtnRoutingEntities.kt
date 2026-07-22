package spike.dtnrouting.domain

import spike.identity.domain.NodeId

data class DtnEnvelope(
    val protocolVersion: String = "1.0",
    val envelopeId: EnvelopeId = EnvelopeId.generate(),
    val sourceNodeId: NodeId,
    val destinationNodeId: NodeId,
    val payloadType: String,
    val inlinePayloadCiphertext: ByteArray? = null,
    val payloadObjectReferenceHash: String? = null,
    val payloadLength: Long,
    val payloadCiphertextHash: PayloadCiphertextHash,
    val routingStrategyId: RoutingStrategyId,
    val priority: EnvelopePriority = EnvelopePriority.NORMAL,
    val createdAt: Long,
    val ttlDuration: EnvelopeTtl,
    val hopLimit: HopLimit,
    val initialCopyBudget: CopyBudget,
    val sourceAuthenticationProof: String,
    val routingMetadata: Map<String, String> = emptyMap()
) {
    val expiresAt: Long get() = createdAt + ttlDuration.durationMillis

    fun isExpiredAt(nowEpochMs: Long): Boolean = nowEpochMs > expiresAt

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DtnEnvelope
        return envelopeId == other.envelopeId
    }

    override fun hashCode(): Int = envelopeId.hashCode()
}

data class StoredEnvelope(
    val envelopeId: EnvelopeId,
    val receivedFromNodeId: NodeId,
    val receivedAt: Long,
    val storedAt: Long,
    val localHopCount: HopCount,
    val remainingCopyBudget: CopyBudget,
    val custodyState: CustodyStateEnum = CustodyStateEnum.REFUSED,
    val envelopeState: EnvelopeStateEnum = EnvelopeStateEnum.STORED,
    val lastForwardedAt: Long? = null,
    val forwardAttemptCount: Int = 0,
    val storageBytes: Long,
    val isLocalSource: Boolean = false,
    val isLocalDestination: Boolean = false
)

data class ForwardingRecord(
    val envelopeId: EnvelopeId,
    val fromNodeId: NodeId,
    val toNodeId: NodeId,
    val encounterId: String,
    val attemptedAt: Long,
    val completedAt: Long?,
    val resultStatus: String,
    val bytesTransferred: Long,
    val custodyRequested: Boolean,
    val custodyAccepted: Boolean,
    val strategyId: RoutingStrategyId
)

data class CustodyRecord(
    val envelopeId: EnvelopeId,
    val custodianNodeId: NodeId,
    val previousCustodianNodeId: NodeId?,
    val state: CustodyStateEnum,
    val acceptedAt: Long,
    val expiresAt: Long,
    val transferredAt: Long? = null,
    val releasedAt: Long? = null,
    val releaseReason: String? = null
)

data class DeliveryReceipt(
    val receiptId: ReceiptId = ReceiptId.generate(),
    val envelopeId: EnvelopeId,
    val sourceNodeId: NodeId,
    val destinationNodeId: NodeId,
    val deliveredAt: Long,
    val payloadCiphertextHash: PayloadCiphertextHash,
    val deliveryStatus: DeliveryStatusEnum,
    val destinationProof: String,
    val receiptTtl: EnvelopeTtl,
    val createdAt: Long
) {
    val expiresAt: Long get() = createdAt + receiptTtl.durationMillis
}

/**
 * Tombstone record preserved to prevent duplicate delivery after delayed encounters.
 * Invariant: Must be preserved until explicit garbage collection policy expiration.
 */
data class DeliveredEnvelopeTombstone(
    val envelopeId: EnvelopeId,
    val destinationNodeId: NodeId,
    val payloadCiphertextHash: PayloadCiphertextHash,
    val receiptId: ReceiptId,
    val deliveredAt: Long,
    val createdAt: Long,
    val expiresAt: Long
) {
    fun isExpiredAt(nowEpochMs: Long): Boolean = nowEpochMs > expiresAt
}

data class EncounterRoutingState(
    val peerNodeId: NodeId,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val encounterCount: Long,
    val totalContactDurationMs: Long,
    val averageContactDurationMs: Long,
    val lastSuccessfulForwardAt: Long? = null,
    val updatedAt: Long
)

data class DestinationPredictabilityRecord(
    val destinationNodeId: NodeId,
    val score: PredictabilityScore,
    val lastUpdated: Long
)

data class RoutingSummary(
    val summaryId: String,
    val nodeId: NodeId,
    val summaryVersion: Long,
    val generatedAt: Long,
    val knownEnvelopeIds: List<EnvelopeId>,
    val deliveredTombstoneIds: List<EnvelopeId>,
    val availableStorageBytes: Long,
    val backpressureLevel: BackpressureLevel = BackpressureLevel.NORMAL
)

data class RoutingTransferPlan(
    val planId: String,
    val localNodeId: NodeId,
    val remoteNodeId: NodeId,
    val encounterId: String,
    val createdAt: Long,
    val strategyId: RoutingStrategyId,
    val selectedEnvelopeIds: List<EnvelopeId>,
    val rejectedEnvelopeIds: Map<EnvelopeId, String> = emptyMap(),
    val estimatedTransferBytes: Long,
    val custodyRequestedEnvelopeIds: Set<EnvelopeId> = emptySet()
)
