package spike.dtnrouting.application

import spike.dtnrouting.contracts.CustodyManager
import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository
import spike.identity.domain.NodeId

/**
 * Exclusive authority responsible for custody acquisition, custody release, and Custody ACK generation.
 * Invariant: CUSTODY ACK MUST REQUIRE DURABLE ENVELOPE + CUSTODY RECORD COMMIT.
 */
class CustodyManagerImpl(
    private val localNodeId: NodeId,
    private val repository: DtnRoutingRepository
) : CustodyManager {

    override suspend fun processCustodyRequest(
        requestingNodeId: NodeId,
        envelope: DtnEnvelope
    ): CustodyRecord {
        val now = System.currentTimeMillis()

        // 1. Check if envelope already exists and is durably persisted
        val storedState = repository.getStoredEnvelopeState(envelope.envelopeId)
            ?: throw DtnRoutingError.CustodyRefused(envelope.envelopeId, "Envelope not committed to local storage")

        // 2. Validate envelope not expired
        if (envelope.isExpiredAt(now)) {
            throw DtnRoutingError.CustodyRefused(envelope.envelopeId, "Envelope expired")
        }

        // 3. Reserve storage quota
        val reserved = repository.reserveStorage("custody_${envelope.envelopeId.value}", envelope.payloadLength)
        if (!reserved) {
            throw DtnRoutingError.CustodyRefused(envelope.envelopeId, "Storage quota exceeded")
        }

        // 4. Update stored envelope custody state to ACCEPTED
        val updatedStored = storedState.copy(custodyState = CustodyStateEnum.ACCEPTED)
        repository.saveStoredEnvelopeState(updatedStored)

        // 5. Create and durably commit CustodyRecord BEFORE Custody ACK generation
        val custodyRecord = CustodyRecord(
            envelopeId = envelope.envelopeId,
            custodianNodeId = localNodeId,
            previousCustodianNodeId = requestingNodeId,
            state = CustodyStateEnum.ACTIVE,
            acceptedAt = now,
            expiresAt = envelope.expiresAt
        )
        repository.saveCustodyRecord(custodyRecord)

        return custodyRecord
    }

    override suspend fun processCustodyAck(
        ackCustodianNodeId: NodeId,
        envelopeId: EnvelopeId
    ): CustodyRecord {
        val now = System.currentTimeMillis()
        val existing = repository.getCustodyRecord(envelopeId)
            ?: throw DtnRoutingError.CustodyRefused(envelopeId, "No active custody record found")

        val updated = existing.copy(
            state = CustodyStateEnum.TRANSFERRED,
            transferredAt = now
        )
        repository.saveCustodyRecord(updated)

        // Current custodian can now release payload after confirmed custody ACK from new custodian
        val stored = repository.getStoredEnvelopeState(envelopeId)
        if (stored != null) {
            repository.saveStoredEnvelopeState(stored.copy(custodyState = CustodyStateEnum.TRANSFERRED))
        }

        return updated
    }

    override suspend fun releaseCustody(
        envelopeId: EnvelopeId,
        reason: String
    ): CustodyRecord {
        val now = System.currentTimeMillis()
        val existing = repository.getCustodyRecord(envelopeId)
            ?: throw DtnRoutingError.CustodyRefused(envelopeId, "No custody record found")

        val released = existing.copy(
            state = CustodyStateEnum.RELEASED,
            releasedAt = now,
            releaseReason = reason
        )
        repository.saveCustodyRecord(released)
        repository.releaseStorageReservation("custody_${envelopeId.value}")

        return released
    }
}
