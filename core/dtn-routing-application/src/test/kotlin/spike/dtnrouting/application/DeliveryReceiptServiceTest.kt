package spike.dtnrouting.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

class DeliveryReceiptServiceTest {

    private lateinit var repository: InMemoryDtnRoutingRepository
    private lateinit var receiptService: DeliveryReceiptServiceImpl

    private val localNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    private val srcNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    @BeforeEach
    fun setUp() {
        repository = InMemoryDtnRoutingRepository()
        receiptService = DeliveryReceiptServiceImpl(localNode, repository)
    }

    @Test
    fun `Creating delivery receipt produces valid destination proof signature`() = runBlocking {
        val now = System.currentTimeMillis()
        val envelope = DtnEnvelope(
            envelopeId = EnvelopeId("env_rcpt_1"),
            sourceNodeId = srcNode,
            destinationNodeId = localNode,
            payloadType = "TEST",
            payloadLength = 100L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_rcpt_1"),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4),
            sourceAuthenticationProof = "proof_rcpt_1"
        )

        val receipt = receiptService.createDeliveryReceipt(envelope, now)
        assertEquals(envelope.envelopeId, receipt.envelopeId)
        assertEquals(localNode, receipt.destinationNodeId)
        assertTrue(receipt.destinationProof.contains(localNode.value))
        assertTrue(receipt.destinationProof.contains(envelope.envelopeId.value))

        Unit
    }

    @Test
    fun `Verifying valid delivery receipt creates and durably commits DeliveredEnvelopeTombstone`() = runBlocking {
        val now = System.currentTimeMillis()
        val envelope = DtnEnvelope(
            envelopeId = EnvelopeId("env_rcpt_2"),
            sourceNodeId = srcNode,
            destinationNodeId = localNode,
            payloadType = "TEST",
            payloadLength = 100L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_rcpt_2"),
            routingStrategyId = RoutingStrategyId("DIRECT_ONLY"),
            priority = EnvelopePriority.NORMAL,
            createdAt = now,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(5),
            initialCopyBudget = CopyBudget(4),
            sourceAuthenticationProof = "proof_rcpt_2"
        )

        val receipt = receiptService.createDeliveryReceipt(envelope, now)
        val tombstone = receiptService.verifyAndProcessReceipt(receipt)

        assertEquals(envelope.envelopeId, tombstone.envelopeId)
        assertEquals(localNode, tombstone.destinationNodeId)
        assertEquals(receipt.receiptId, tombstone.receiptId)

        // Verify tombstone is saved in repository
        val savedTombstone = repository.getTombstone(envelope.envelopeId)
        assertNotNull(savedTombstone)
        assertEquals(receipt.receiptId, savedTombstone?.receiptId)

        Unit
    }
}
