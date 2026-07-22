package spike.dtnrouting.application

import spike.dtnrouting.contracts.*
import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository
import spike.identity.domain.NodeId
import spike.security.contracts.LocalPeerSecurityCore
import spike.security.domain.*
import java.util.concurrent.CopyOnWriteArrayList

class DelayTolerantRoutingCoreImpl(
    private val localNodeId: NodeId,
    private val repository: DtnRoutingRepository,
    private val localPeerSecurityCore: LocalPeerSecurityCore,
    private val envelopeService: DtnEnvelopeServiceImpl,
    private val forwardingEngine: ForwardingEngineImpl,
    private val custodyManager: CustodyManagerImpl,
    private val deliveryReceiptService: DeliveryReceiptServiceImpl,
    private val garbageCollector: DtnGarbageCollectorImpl,
    private val recoveryService: DtnRecoveryServiceImpl
) : DelayTolerantRoutingCore {

    private val listeners = CopyOnWriteArrayList<DtnEventListener>()

    override suspend fun createEnvelope(
        destinationNodeId: NodeId,
        payloadType: String,
        payloadBytes: ByteArray,
        routingStrategyId: RoutingStrategyId,
        priority: EnvelopePriority,
        ttlDuration: EnvelopeTtl,
        hopLimit: HopLimit,
        initialCopyBudget: CopyBudget
    ): DtnEnvelope {
        val envelope = envelopeService.createEnvelope(
            destinationNodeId = destinationNodeId,
            payloadType = payloadType,
            payloadBytes = payloadBytes,
            routingStrategyId = routingStrategyId,
            priority = priority,
            ttlDuration = ttlDuration,
            hopLimit = hopLimit,
            initialCopyBudget = initialCopyBudget
        )

        // Local source envelope persistence
        val now = System.currentTimeMillis()
        repository.saveEnvelope(envelope)

        val storedState = StoredEnvelope(
            envelopeId = envelope.envelopeId,
            receivedFromNodeId = localNodeId,
            receivedAt = now,
            storedAt = now,
            localHopCount = HopCount(0),
            remainingCopyBudget = initialCopyBudget,
            custodyState = CustodyStateEnum.ACTIVE,
            envelopeState = EnvelopeStateEnum.STORED,
            storageBytes = envelope.payloadLength,
            isLocalSource = true,
            isLocalDestination = localNodeId == destinationNodeId
        )
        repository.saveStoredEnvelopeState(storedState)

        notifyListeners("ENVELOPE_CREATED", mapOf("envelopeId" to envelope.envelopeId.value, "destination" to destinationNodeId.value))
        return envelope
    }

    override suspend fun getEnvelope(envelopeId: EnvelopeId): DtnEnvelope? = repository.getEnvelope(envelopeId)

    override suspend fun getStoredEnvelopeState(envelopeId: EnvelopeId): StoredEnvelope? = repository.getStoredEnvelopeState(envelopeId)

    override suspend fun listEnvelopes(): List<DtnEnvelope> = repository.listEnvelopes()

    override suspend fun buildRoutingSummary(remotePeerNodeId: NodeId): RoutingSummary {
        val now = System.currentTimeMillis()
        val allStored = repository.findStoredEnvelopes { true }
        val knownIds = allStored.map { it.envelopeId }
        val tombstones = repository.listTombstones().map { it.envelopeId }

        val version = (repository.getRoutingSummaryVersion(localNodeId) ?: 0L) + 1L
        repository.saveRoutingSummaryVersion(localNodeId, version)

        return RoutingSummary(
            summaryId = "sum_${localNodeId.value}_$version",
            nodeId = localNodeId,
            summaryVersion = version,
            generatedAt = now,
            knownEnvelopeIds = knownIds,
            deliveredTombstoneIds = tombstones,
            availableStorageBytes = 100 * 1024 * 1024L
        )
    }

    override suspend fun processRoutingSummary(remotePeerNodeId: NodeId, summary: RoutingSummary): RoutingTransferPlan {
        return forwardingEngine.buildTransferPlan("enc_${remotePeerNodeId.value}", remotePeerNodeId, summary)
    }

    override suspend fun executeTransferPlan(plan: RoutingTransferPlan): List<ForwardingRecord> {
        val records = forwardingEngine.executeTransferPlan(plan)
        notifyListeners("TRANSFER_PLAN_EXECUTED", mapOf("planId" to plan.planId, "count" to records.size.toString()))
        return records
    }

    override suspend fun processInboundEnvelope(
        senderNodeId: NodeId,
        envelope: DtnEnvelope
    ): StoredEnvelope {
        val now = System.currentTimeMillis()

        // 1. Validate envelope integrity and expiration
        envelopeService.validateEnvelope(envelope)

        // 2. Check Tombstone duplicate suppression
        val tombstone = repository.getTombstone(envelope.envelopeId)
        if (tombstone != null) {
            throw DtnRoutingError.DuplicateDeliverySuppressed(envelope.envelopeId)
        }

        // 3. SPEC-009 Authorization Gateway Verification (MUST OCCUR BEFORE DURABLE RELAY STORAGE COMMIT)
        val authRequest = AuthorizationRequest(
            peerNodeId = senderNodeId,
            capabilityId = CapabilityId("network.relay.accept"),
            operationType = OperationType("RELAY_ACCEPT"),
            resourceType = ResourceType("ENVELOPE"),
            resourceId = envelope.envelopeId.value,
            requestedUsage = envelope.payloadLength,
            context = AuthorizationContext(payloadBytes = envelope.payloadLength),
            requestedAt = now
        )

        val decision = localPeerSecurityCore.authorize(authRequest)
        if (decision.effect != AuthorizationEffect.ALLOW) {
            throw DtnRoutingError.RelayUnauthorized(senderNodeId, decision.reasonCode.name)
        }

        // 4. Reserve storage
        val reserved = repository.reserveStorage("relay_${envelope.envelopeId.value}", envelope.payloadLength)
        if (!reserved) {
            throw DtnRoutingError.StorageQuotaExceeded(envelope.payloadLength, 0L)
        }

        // 5. Durable commit of envelope and stored state
        repository.saveEnvelope(envelope)

        val isLocalDest = envelope.destinationNodeId == localNodeId
        val storedState = StoredEnvelope(
            envelopeId = envelope.envelopeId,
            receivedFromNodeId = senderNodeId,
            receivedAt = now,
            storedAt = now,
            localHopCount = HopCount(1),
            remainingCopyBudget = envelope.initialCopyBudget,
            custodyState = CustodyStateEnum.REFUSED,
            envelopeState = if (isLocalDest) EnvelopeStateEnum.DELIVERED else EnvelopeStateEnum.STORED,
            storageBytes = envelope.payloadLength,
            isLocalSource = envelope.sourceNodeId == localNodeId,
            isLocalDestination = isLocalDest
        )
        repository.saveStoredEnvelopeState(storedState)

        // 6. If local destination, create delivery receipt
        if (isLocalDest) {
            deliveryReceiptService.createDeliveryReceipt(envelope, now)
        }

        notifyListeners("INBOUND_ENVELOPE_STORED", mapOf("envelopeId" to envelope.envelopeId.value, "sender" to senderNodeId.value))
        return storedState
    }

    override suspend fun processDeliveryReceipt(
        senderNodeId: NodeId,
        receipt: DeliveryReceipt
    ): DeliveredEnvelopeTombstone {
        val tombstone = deliveryReceiptService.verifyAndProcessReceipt(receipt)
        notifyListeners("DELIVERY_RECEIPT_PROCESSED", mapOf("receiptId" to receipt.receiptId.value, "envelopeId" to receipt.envelopeId.value))
        return tombstone
    }

    override suspend fun runGarbageCollection(): Int {
        val count = garbageCollector.runGarbageCollection()
        notifyListeners("GARBAGE_COLLECTION_COMPLETED", mapOf("count" to count.toString()))
        return count
    }

    override suspend fun recover() {
        recoveryService.recover()
        notifyListeners("RECOVERY_COMPLETED", emptyMap())
    }

    override fun subscribeDtnEvents(listener: DtnEventListener) {
        listeners.add(listener)
    }

    private fun notifyListeners(eventType: String, details: Map<String, String>) {
        listeners.forEach { it.onDtnEvent(eventType, details) }
    }
}
