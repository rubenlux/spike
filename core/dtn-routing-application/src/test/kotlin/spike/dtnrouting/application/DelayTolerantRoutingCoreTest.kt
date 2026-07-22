package spike.dtnrouting.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

class DelayTolerantRoutingCoreTest {

    private lateinit var repository: InMemoryDtnRoutingRepository
    private lateinit var fakeSecurityCore: FakeLocalPeerSecurityCore
    private lateinit var envelopeService: DtnEnvelopeServiceImpl
    private lateinit var forwardingEngine: ForwardingEngineImpl
    private lateinit var custodyManager: CustodyManagerImpl
    private lateinit var receiptService: DeliveryReceiptServiceImpl
    private lateinit var gc: DtnGarbageCollectorImpl
    private lateinit var recoveryService: DtnRecoveryServiceImpl
    private lateinit var core: DelayTolerantRoutingCoreImpl

    private val localNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    private val senderNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")
    private val destNode = NodeId("node:v1:sha256:3333333333333333333333333333333333333333333333333333333333333333")

    @BeforeEach
    fun setUp() {
        repository = InMemoryDtnRoutingRepository()
        fakeSecurityCore = FakeLocalPeerSecurityCore()
        envelopeService = DtnEnvelopeServiceImpl(localNode)
        forwardingEngine = ForwardingEngineImpl(localNode, repository)
        custodyManager = CustodyManagerImpl(localNode, repository)
        receiptService = DeliveryReceiptServiceImpl(localNode, repository)
        gc = DtnGarbageCollectorImpl(repository)
        recoveryService = DtnRecoveryServiceImpl(repository)

        core = DelayTolerantRoutingCoreImpl(
            localNodeId = localNode,
            repository = repository,
            localPeerSecurityCore = fakeSecurityCore,
            envelopeService = envelopeService,
            forwardingEngine = forwardingEngine,
            custodyManager = custodyManager,
            deliveryReceiptService = receiptService,
            garbageCollector = gc,
            recoveryService = recoveryService
        )
    }

    @Test
    fun `SPEC-009 authorization denial blocks inbound relay storage commit`() = runBlocking {
        // network.relay.accept is NOT in fakeSecurityCore.allowedCapabilities
        val envelope = envelopeService.createEnvelope(
            destinationNodeId = destNode,
            payloadType = "TEST",
            payloadBytes = "hello".toByteArray(),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4)
        )

        var caughtUnauthorized: DtnRoutingError.RelayUnauthorized? = null
        try {
            core.processInboundEnvelope(senderNode, envelope)
        } catch (e: DtnRoutingError.RelayUnauthorized) {
            caughtUnauthorized = e
        }

        assertNotNull(caughtUnauthorized)
        assertTrue(caughtUnauthorized.message.contains("DENIED_NO_MATCHING_GRANT"))

        // Durable commit MUST NOT have occurred
        val storedState = repository.getStoredEnvelopeState(envelope.envelopeId)
        assertNull(storedState)

        Unit
    }

    @Test
    fun `SPEC-009 authorization approval allows durable inbound relay storage commit`() = runBlocking {
        fakeSecurityCore.allowedCapabilities.add("network.relay.accept")

        val envelope = envelopeService.createEnvelope(
            destinationNodeId = destNode,
            payloadType = "TEST",
            payloadBytes = "hello".toByteArray(),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4)
        )

        val stored = core.processInboundEnvelope(senderNode, envelope)
        assertNotNull(stored)
        assertEquals(EnvelopeStateEnum.STORED, stored.envelopeState)
        assertEquals(senderNode, stored.receivedFromNodeId)

        // Envelope was committed to storage
        val savedEnvelope = repository.getEnvelope(envelope.envelopeId)
        assertNotNull(savedEnvelope)

        Unit
    }

    @Test
    fun `Tombstone duplicate suppression rejects inbound envelope when tombstone exists`() = runBlocking {
        fakeSecurityCore.allowedCapabilities.add("network.relay.accept")

        val envelope = envelopeService.createEnvelope(
            destinationNodeId = localNode,
            payloadType = "TEST",
            payloadBytes = "hello".toByteArray(),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4)
        )

        // Save explicit tombstone
        val tombstone = DeliveredEnvelopeTombstone(
            envelopeId = envelope.envelopeId,
            destinationNodeId = localNode,
            payloadCiphertextHash = envelope.payloadCiphertextHash,
            receiptId = ReceiptId("rcpt_123"),
            deliveredAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86_400_000L
        )
        repository.saveTombstone(tombstone)

        var caughtDuplicate: DtnRoutingError.DuplicateDeliverySuppressed? = null
        try {
            core.processInboundEnvelope(senderNode, envelope)
        } catch (e: DtnRoutingError.DuplicateDeliverySuppressed) {
            caughtDuplicate = e
        }

        assertNotNull(caughtDuplicate)
        assertEquals(envelope.envelopeId, caughtDuplicate!!.envelopeId)

        Unit
    }
}
