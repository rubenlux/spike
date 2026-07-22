package spike.dtnrouting.application

import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository
import spike.identity.domain.NodeId
import java.util.concurrent.ConcurrentHashMap

class InMemoryDtnRoutingRepository : DtnRoutingRepository {
    val envelopes = ConcurrentHashMap<EnvelopeId, DtnEnvelope>()
    val storedStates = ConcurrentHashMap<EnvelopeId, StoredEnvelope>()
    val forwardingRecords = ConcurrentHashMap<String, ForwardingRecord>()
    val custodyRecords = ConcurrentHashMap<EnvelopeId, CustodyRecord>()
    val receipts = ConcurrentHashMap<ReceiptId, DeliveryReceipt>()
    val tombstones = ConcurrentHashMap<EnvelopeId, DeliveredEnvelopeTombstone>()
    val encounterStates = ConcurrentHashMap<NodeId, EncounterRoutingState>()
    val predictabilityRecords = ConcurrentHashMap<NodeId, DestinationPredictabilityRecord>()
    val summaryVersions = ConcurrentHashMap<NodeId, Long>()

    override suspend fun saveEnvelope(envelope: DtnEnvelope) { envelopes[envelope.envelopeId] = envelope }
    override suspend fun getEnvelope(envelopeId: EnvelopeId): DtnEnvelope? = envelopes[envelopeId]
    override suspend fun listEnvelopes(): List<DtnEnvelope> = envelopes.values.toList()

    override suspend fun saveStoredEnvelopeState(state: StoredEnvelope) { storedStates[state.envelopeId] = state }
    override suspend fun getStoredEnvelopeState(envelopeId: EnvelopeId): StoredEnvelope? = storedStates[envelopeId]
    override suspend fun findStoredEnvelopes(filter: (StoredEnvelope) -> Boolean): List<StoredEnvelope> = storedStates.values.filter(filter)
    override suspend fun deleteStoredEnvelopeState(envelopeId: EnvelopeId) { storedStates.remove(envelopeId) }

    override suspend fun saveForwardingRecord(record: ForwardingRecord) {
        forwardingRecords["${record.envelopeId.value}_${record.attemptedAt}"] = record
    }
    override suspend fun findForwardingRecords(envelopeId: EnvelopeId): List<ForwardingRecord> =
        forwardingRecords.values.filter { it.envelopeId == envelopeId }
    override suspend fun listForwardingRecords(): List<ForwardingRecord> = forwardingRecords.values.toList()

    override suspend fun saveCustodyRecord(record: CustodyRecord) { custodyRecords[record.envelopeId] = record }
    override suspend fun getCustodyRecord(envelopeId: EnvelopeId): CustodyRecord? = custodyRecords[envelopeId]
    override suspend fun findCustodyRecords(state: CustodyStateEnum?): List<CustodyRecord> =
        custodyRecords.values.filter { state == null || it.state == state }

    override suspend fun saveReceipt(receipt: DeliveryReceipt) { receipts[receipt.receiptId] = receipt }
    override suspend fun getReceipt(receiptId: ReceiptId): DeliveryReceipt? = receipts[receiptId]
    override suspend fun getReceiptByEnvelope(envelopeId: EnvelopeId): DeliveryReceipt? =
        receipts.values.find { it.envelopeId == envelopeId }

    override suspend fun saveTombstone(tombstone: DeliveredEnvelopeTombstone) { tombstones[tombstone.envelopeId] = tombstone }
    override suspend fun getTombstone(envelopeId: EnvelopeId): DeliveredEnvelopeTombstone? = tombstones[envelopeId]
    override suspend fun listTombstones(): List<DeliveredEnvelopeTombstone> = tombstones.values.toList()
    override suspend fun deleteTombstone(envelopeId: EnvelopeId) { tombstones.remove(envelopeId) }

    override suspend fun saveEncounterRoutingState(state: EncounterRoutingState) { encounterStates[state.peerNodeId] = state }
    override suspend fun getEncounterRoutingState(peerNodeId: NodeId): EncounterRoutingState? = encounterStates[peerNodeId]
    override suspend fun listEncounterRoutingStates(): List<EncounterRoutingState> = encounterStates.values.toList()

    override suspend fun savePredictabilityRecord(record: DestinationPredictabilityRecord) { predictabilityRecords[record.destinationNodeId] = record }
    override suspend fun getPredictabilityRecord(destinationNodeId: NodeId): DestinationPredictabilityRecord? = predictabilityRecords[destinationNodeId]
    override suspend fun listPredictabilityRecords(): List<DestinationPredictabilityRecord> = predictabilityRecords.values.toList()

    override suspend fun saveRoutingSummaryVersion(nodeId: NodeId, version: Long) { summaryVersions[nodeId] = version }
    override suspend fun getRoutingSummaryVersion(nodeId: NodeId): Long? = summaryVersions[nodeId]

    override suspend fun reserveStorage(reservationId: String, requestedBytes: Long): Boolean = true
    override suspend fun releaseStorageReservation(reservationId: String) {}
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}
