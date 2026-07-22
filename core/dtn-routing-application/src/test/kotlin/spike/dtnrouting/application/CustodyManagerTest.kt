package spike.dtnrouting.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

class CustodyManagerTest {

    private lateinit var repository: InMemoryDtnRoutingRepository
    private lateinit var custodyManager: CustodyManagerImpl

    private val localNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    private val peerNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    @BeforeEach
    fun setUp() {
        repository = InMemoryDtnRoutingRepository()
        custodyManager = CustodyManagerImpl(localNode, repository)
    }

    @Test
    fun `Custody acquisition requires durable envelope storage commit prior to custody record creation`() = runBlocking {
        val now = System.currentTimeMillis()
        val envelope = DtnEnvelope(
            envelopeId = EnvelopeId("env_custody_1"),
            sourceNodeId = peerNode,
            destinationNodeId = localNode,
            payloadType = "TEST",
            payloadLength = 200L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_c1"),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4),
            sourceAuthenticationProof = "proof_c1"
        )

        // Attempting custody processing without durable envelope commit throws CustodyRefused
        var caughtRefused: DtnRoutingError.CustodyRefused? = null
        try {
            custodyManager.processCustodyRequest(peerNode, envelope)
        } catch (e: DtnRoutingError.CustodyRefused) {
            caughtRefused = e
        }
        assertNotNull(caughtRefused)

        // Save durable envelope and stored state
        repository.saveEnvelope(envelope)
        repository.saveStoredEnvelopeState(StoredEnvelope(envelope.envelopeId, peerNode, now, now, HopCount(1), CopyBudget(4), storageBytes = 200L))

        // Now custody acquisition succeeds
        val record = custodyManager.processCustodyRequest(peerNode, envelope)
        assertEquals(CustodyStateEnum.ACTIVE, record.state)
        assertEquals(localNode, record.custodianNodeId)
        assertEquals(peerNode, record.previousCustodianNodeId)

        // Verify stored state custody updated to ACCEPTED
        val updatedStored = repository.getStoredEnvelopeState(envelope.envelopeId)
        assertEquals(CustodyStateEnum.ACCEPTED, updatedStored?.custodyState)

        Unit
    }

    @Test
    fun `Custody ACK transitions record to TRANSFERRED state`() = runBlocking {
        val now = System.currentTimeMillis()
        val envelope = DtnEnvelope(
            envelopeId = EnvelopeId("env_custody_2"),
            sourceNodeId = localNode,
            destinationNodeId = peerNode,
            payloadType = "TEST",
            payloadLength = 100L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_c2"),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4),
            sourceAuthenticationProof = "proof_c2"
        )

        repository.saveEnvelope(envelope)
        repository.saveStoredEnvelopeState(StoredEnvelope(envelope.envelopeId, localNode, now, now, HopCount(0), CopyBudget(4), storageBytes = 100L))

        custodyManager.processCustodyRequest(peerNode, envelope)

        val updatedRecord = custodyManager.processCustodyAck(peerNode, envelope.envelopeId)
        assertEquals(CustodyStateEnum.TRANSFERRED, updatedRecord.state)
        assertNotNull(updatedRecord.transferredAt)

        Unit
    }
}
