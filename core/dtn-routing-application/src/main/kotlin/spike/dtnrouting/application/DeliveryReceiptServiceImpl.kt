package spike.dtnrouting.application

import spike.dtnrouting.contracts.DeliveryReceiptService
import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository
import spike.identity.domain.NodeId

class DeliveryReceiptServiceImpl(
    private val localNodeId: NodeId,
    private val repository: DtnRoutingRepository
) : DeliveryReceiptService {

    override suspend fun createDeliveryReceipt(
        envelope: DtnEnvelope,
        deliveredAt: Long
    ): DeliveryReceipt {
        val proof = generateDestinationProof(localNodeId, envelope.envelopeId, envelope.payloadCiphertextHash.value, deliveredAt)
        val receipt = DeliveryReceipt(
            receiptId = ReceiptId.generate(),
            envelopeId = envelope.envelopeId,
            sourceNodeId = envelope.sourceNodeId,
            destinationNodeId = envelope.destinationNodeId,
            deliveredAt = deliveredAt,
            payloadCiphertextHash = envelope.payloadCiphertextHash,
            deliveryStatus = DeliveryStatusEnum.SUCCESS,
            destinationProof = proof,
            receiptTtl = envelope.ttlDuration,
            createdAt = deliveredAt
        )

        repository.saveReceipt(receipt)
        return receipt
    }

    override suspend fun verifyAndProcessReceipt(
        receipt: DeliveryReceipt
    ): DeliveredEnvelopeTombstone {
        val now = System.currentTimeMillis()

        // 1. Verify destination proof signature format
        if (!verifyDestinationProof(receipt)) {
            throw DtnRoutingError.ReceiptAuthenticationFailed(receipt.receiptId)
        }

        // 2. Persist receipt
        repository.saveReceipt(receipt)

        // 3. Create and durably commit DeliveredEnvelopeTombstone
        // Invariant: Tombstone MUST be preserved until explicit GC policy expiration
        val tombstone = DeliveredEnvelopeTombstone(
            envelopeId = receipt.envelopeId,
            destinationNodeId = receipt.destinationNodeId,
            payloadCiphertextHash = receipt.payloadCiphertextHash,
            receiptId = receipt.receiptId,
            deliveredAt = receipt.deliveredAt,
            createdAt = now,
            expiresAt = receipt.expiresAt
        )
        repository.saveTombstone(tombstone)

        // 4. Update stored envelope state to DELIVERED if locally stored
        val stored = repository.getStoredEnvelopeState(receipt.envelopeId)
        if (stored != null) {
            repository.saveStoredEnvelopeState(stored.copy(envelopeState = EnvelopeStateEnum.DELIVERED))
        }

        return tombstone
    }

    override suspend fun getTombstone(envelopeId: EnvelopeId): DeliveredEnvelopeTombstone? {
        return repository.getTombstone(envelopeId)
    }

    private fun generateDestinationProof(
        dstNodeId: NodeId,
        envelopeId: EnvelopeId,
        hash: String,
        deliveredAt: Long
    ): String {
        return "PROOF_DST_v1_${dstNodeId.value}_${envelopeId.value}_${hash}_$deliveredAt"
    }

    private fun verifyDestinationProof(receipt: DeliveryReceipt): Boolean {
        val expectedPrefix = "PROOF_DST_v1_${receipt.destinationNodeId.value}_${receipt.envelopeId.value}"
        return receipt.destinationProof.startsWith(expectedPrefix)
    }
}
