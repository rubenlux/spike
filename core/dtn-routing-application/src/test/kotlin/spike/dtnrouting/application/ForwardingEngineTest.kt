package spike.dtnrouting.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

class ForwardingEngineTest {

    private lateinit var repositoryDtn: InMemoryDtnRoutingRepository
    private lateinit var forwardingEngine: ForwardingEngineImpl

    private val localNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    private val remoteNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    @BeforeEach
    fun setUp() {
        repositoryDtn = InMemoryDtnRoutingRepository()
        forwardingEngine = ForwardingEngineImpl(localNode, repositoryDtn)
    }

    @Test
    fun `ForwardingEngine sorts candidates deterministically using EnvelopeId tie-breaker`() = runBlocking {
        val now = System.currentTimeMillis()

        // Create 2 envelopes with SAME priority and SAME createdAt timestamp
        val envB = DtnEnvelope(
            envelopeId = EnvelopeId("env_B_id"),
            sourceNodeId = localNode,
            destinationNodeId = remoteNode,
            payloadType = "TEST",
            payloadLength = 100L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_B"),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4),
            sourceAuthenticationProof = "proof_B"
        )

        val envA = DtnEnvelope(
            envelopeId = EnvelopeId("env_A_id"),
            sourceNodeId = localNode,
            destinationNodeId = remoteNode,
            payloadType = "TEST",
            payloadLength = 100L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_A"),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4),
            sourceAuthenticationProof = "proof_A"
        )

        // Save envB then envA in arbitrary order
        repositoryDtn.saveEnvelope(envB)
        repositoryDtn.saveStoredEnvelopeState(StoredEnvelope(envB.envelopeId, localNode, now, now, HopCount(0), CopyBudget(4), storageBytes = 100L))
        repositoryDtn.saveEnvelope(envA)
        repositoryDtn.saveStoredEnvelopeState(StoredEnvelope(envA.envelopeId, localNode, now, now, HopCount(0), CopyBudget(4), storageBytes = 100L))

        val summary = RoutingSummary("sum_1", remoteNode, 1L, now, emptyList(), emptyList(), 10_000_000L)
        val plan = forwardingEngine.buildTransferPlan("enc_1", remoteNode, summary)

        // Deterministic sorting MUST order env_A_id before env_B_id lexicographically
        assertEquals(2, plan.selectedEnvelopeIds.size)
        assertEquals("env_A_id", plan.selectedEnvelopeIds[0].value)
        assertEquals("env_B_id", plan.selectedEnvelopeIds[1].value)

        Unit
    }

    @Test
    fun `ForwardingEngine execution increments local HopCount and splits CopyBudget`() = runBlocking {
        val now = System.currentTimeMillis()
        val env = DtnEnvelope(
            envelopeId = EnvelopeId("env_spray_1"),
            sourceNodeId = localNode,
            destinationNodeId = NodeId("node:v1:sha256:3333333333333333333333333333333333333333333333333333333333333333"),
            payloadType = "TEST",
            payloadLength = 50L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_s1"),
            routingStrategyId = RoutingStrategyId("SPRAY_AND_WAIT"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(8),
            sourceAuthenticationProof = "proof_s1"
        )
        repositoryDtn.saveEnvelope(env)
        repositoryDtn.saveStoredEnvelopeState(StoredEnvelope(env.envelopeId, localNode, now, now, HopCount(0), CopyBudget(8), storageBytes = 50L))

        val summary = RoutingSummary("sum_2", remoteNode, 1L, now, emptyList(), emptyList(), 10_000_000L)
        val plan = forwardingEngine.buildTransferPlan("enc_2", remoteNode, summary)
        val records = forwardingEngine.executeTransferPlan(plan)

        assertEquals(1, records.size)

        val updatedStored = repositoryDtn.getStoredEnvelopeState(env.envelopeId)
        assertNotNull(updatedStored)
        assertEquals(1, updatedStored!!.localHopCount.value) // HopCount incremented from 0 -> 1
        assertEquals(4, updatedStored.remainingCopyBudget.value) // CopyBudget split from 8 -> 4

        Unit
    }
}
