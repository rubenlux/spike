package spike.dtnrouting.storage

import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

/**
 * Pure persistence contract interface for Delay-Tolerant Networking storage.
 * Zero primitive obsession — uses strong domain value objects exclusively.
 */
interface DtnRoutingRepository {
    // Envelope storage
    suspend fun saveEnvelope(envelope: DtnEnvelope)
    suspend fun getEnvelope(envelopeId: EnvelopeId): DtnEnvelope?
    suspend fun listEnvelopes(): List<DtnEnvelope>

    // Stored Envelope State (Local Relay State)
    suspend fun saveStoredEnvelopeState(state: StoredEnvelope)
    suspend fun getStoredEnvelopeState(envelopeId: EnvelopeId): StoredEnvelope?
    suspend fun findStoredEnvelopes(filter: (StoredEnvelope) -> Boolean): List<StoredEnvelope>
    suspend fun deleteStoredEnvelopeState(envelopeId: EnvelopeId)

    // Forwarding History
    suspend fun saveForwardingRecord(record: ForwardingRecord)
    suspend fun findForwardingRecords(envelopeId: EnvelopeId): List<ForwardingRecord>
    suspend fun listForwardingRecords(): List<ForwardingRecord>

    // Custody Records
    suspend fun saveCustodyRecord(record: CustodyRecord)
    suspend fun getCustodyRecord(envelopeId: EnvelopeId): CustodyRecord?
    suspend fun findCustodyRecords(state: CustodyStateEnum? = null): List<CustodyRecord>

    // Delivery Receipts & Tombstones
    suspend fun saveReceipt(receipt: DeliveryReceipt)
    suspend fun getReceipt(receiptId: ReceiptId): DeliveryReceipt?
    suspend fun getReceiptByEnvelope(envelopeId: EnvelopeId): DeliveryReceipt?
    suspend fun saveTombstone(tombstone: DeliveredEnvelopeTombstone)
    suspend fun getTombstone(envelopeId: EnvelopeId): DeliveredEnvelopeTombstone?
    suspend fun listTombstones(): List<DeliveredEnvelopeTombstone>
    suspend fun deleteTombstone(envelopeId: EnvelopeId)

    // Encounter & Routing Predictability State
    suspend fun saveEncounterRoutingState(state: EncounterRoutingState)
    suspend fun getEncounterRoutingState(peerNodeId: NodeId): EncounterRoutingState?
    suspend fun listEncounterRoutingStates(): List<EncounterRoutingState>
    suspend fun savePredictabilityRecord(record: DestinationPredictabilityRecord)
    suspend fun getPredictabilityRecord(destinationNodeId: NodeId): DestinationPredictabilityRecord?
    suspend fun listPredictabilityRecords(): List<DestinationPredictabilityRecord>

    // Routing Summaries
    suspend fun saveRoutingSummaryVersion(nodeId: NodeId, version: Long)
    suspend fun getRoutingSummaryVersion(nodeId: NodeId): Long?

    // Storage Reservations & Transactions
    suspend fun reserveStorage(reservationId: String, requestedBytes: Long): Boolean
    suspend fun releaseStorageReservation(reservationId: String)
    suspend fun <T> transaction(block: suspend () -> T): T
}
