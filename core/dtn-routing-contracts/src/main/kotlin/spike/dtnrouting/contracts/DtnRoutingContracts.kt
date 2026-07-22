package spike.dtnrouting.contracts

import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

/** Listener for audit and lifecycle events in DTN routing. */
fun interface DtnEventListener {
    fun onDtnEvent(eventType: String, details: Map<String, String>)
}

/** Pluggable routing strategy interface per user constraint and SPEC-012 Section 42. */
interface RoutingStrategy {
    val strategyId: RoutingStrategyId
    val strategyType: RoutingStrategyType

    suspend fun evaluateCandidates(
        localEnvelopes: List<Pair<DtnEnvelope, StoredEnvelope>>,
        remotePeerNodeId: NodeId,
        remoteSummary: RoutingSummary
    ): List<DtnEnvelope>

    suspend fun calculateCopyAllocation(
        envelope: DtnEnvelope,
        currentCopyBudget: CopyBudget
    ): Pair<CopyBudget, CopyBudget>
}

/** Forwarding Engine interface orchestrating pluggable routing strategies. */
interface ForwardingEngine {
    suspend fun registerStrategy(strategy: RoutingStrategy)
    suspend fun buildTransferPlan(
        encounterId: String,
        remotePeerNodeId: NodeId,
        remoteSummary: RoutingSummary
    ): RoutingTransferPlan
    suspend fun executeTransferPlan(plan: RoutingTransferPlan): List<ForwardingRecord>
}

/** Custody Manager interface for durable custody transfer operations. */
interface CustodyManager {
    suspend fun processCustodyRequest(
        requestingNodeId: NodeId,
        envelope: DtnEnvelope
    ): CustodyRecord

    suspend fun processCustodyAck(
        ackCustodianNodeId: NodeId,
        envelopeId: EnvelopeId
    ): CustodyRecord

    suspend fun releaseCustody(
        envelopeId: EnvelopeId,
        reason: String
    ): CustodyRecord
}

/** Delivery Receipt Service interface. */
interface DeliveryReceiptService {
    suspend fun createDeliveryReceipt(
        envelope: DtnEnvelope,
        deliveredAt: Long
    ): DeliveryReceipt

    suspend fun verifyAndProcessReceipt(
        receipt: DeliveryReceipt
    ): DeliveredEnvelopeTombstone

    suspend fun getTombstone(
        envelopeId: EnvelopeId
    ): DeliveredEnvelopeTombstone?
}

/** Normative public facade contract for Delay-Tolerant Routing Core. */
interface DelayTolerantRoutingCore {
    suspend fun createEnvelope(
        destinationNodeId: NodeId,
        payloadType: String,
        payloadBytes: ByteArray,
        routingStrategyId: RoutingStrategyId,
        priority: EnvelopePriority = EnvelopePriority.NORMAL,
        ttlDuration: EnvelopeTtl = EnvelopeTtl(86_400_000L),
        hopLimit: HopLimit = HopLimit(10),
        initialCopyBudget: CopyBudget = CopyBudget(8)
    ): DtnEnvelope

    suspend fun getEnvelope(envelopeId: EnvelopeId): DtnEnvelope?
    suspend fun getStoredEnvelopeState(envelopeId: EnvelopeId): StoredEnvelope?
    suspend fun listEnvelopes(): List<DtnEnvelope>

    suspend fun buildRoutingSummary(remotePeerNodeId: NodeId): RoutingSummary
    suspend fun processRoutingSummary(remotePeerNodeId: NodeId, summary: RoutingSummary): RoutingTransferPlan
    suspend fun executeTransferPlan(plan: RoutingTransferPlan): List<ForwardingRecord>

    suspend fun processInboundEnvelope(
        senderNodeId: NodeId,
        envelope: DtnEnvelope
    ): StoredEnvelope

    suspend fun processDeliveryReceipt(
        senderNodeId: NodeId,
        receipt: DeliveryReceipt
    ): DeliveredEnvelopeTombstone

    suspend fun runGarbageCollection(): Int
    suspend fun recover()
    fun subscribeDtnEvents(listener: DtnEventListener)
}
