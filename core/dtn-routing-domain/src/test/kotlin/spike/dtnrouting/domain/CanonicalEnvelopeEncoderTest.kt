package spike.dtnrouting.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId

class CanonicalEnvelopeEncoderTest {

    @Test
    fun `Canonical header serialization contains domain tag and envelope fields`() {
        val src = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
        val dst = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

        val envelope = DtnEnvelope(
            envelopeId = EnvelopeId("env_test123"),
            sourceNodeId = src,
            destinationNodeId = dst,
            payloadType = "OFFLINE_MESSAGE",
            payloadLength = 100L,
            payloadCiphertextHash = PayloadCiphertextHash("hash_cipher_123"),
            routingStrategyId = RoutingStrategyId("SPRAY_AND_WAIT"),
            priority = EnvelopePriority.HIGH,
            createdAt = 1000L,
            ttlDuration = EnvelopeTtl(86_400_000L),
            hopLimit = HopLimit(10),
            initialCopyBudget = CopyBudget(8),
            sourceAuthenticationProof = "sig_src_123"
        )

        val headerBytes = CanonicalEnvelopeEncoder.encodeCanonicalHeader(envelope)
        val headerString = String(headerBytes, Charsets.UTF_8)

        assertTrue(headerString.startsWith("OFFLINE_DTN_E2E_ENVELOPE_V1"))
        assertTrue(headerString.contains("id=env_test123"))
        assertTrue(headerString.contains("src=${src.value}"))
        assertTrue(headerString.contains("dst=${dst.value}"))
        assertTrue(headerString.contains("type=OFFLINE_MESSAGE"))
        assertTrue(headerString.contains("hash=hash_cipher_123"))
        assertTrue(headerString.contains("strategy=SPRAY_AND_WAIT"))

        val digest1 = CanonicalEnvelopeEncoder.computeHeaderDigest(envelope)
        val digest2 = CanonicalEnvelopeEncoder.computeHeaderDigest(envelope)
        assertEquals(64, digest1.length) // Hex SHA-256
        assertEquals(digest1, digest2)
    }
}
