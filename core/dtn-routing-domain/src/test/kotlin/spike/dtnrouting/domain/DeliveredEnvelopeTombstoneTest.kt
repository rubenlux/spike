package spike.dtnrouting.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId

class DeliveredEnvelopeTombstoneTest {

    @Test
    fun `DeliveredEnvelopeTombstone remains active until explicitly expired by GC policy`() {
        val dst = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")
        val tombstone = DeliveredEnvelopeTombstone(
            envelopeId = EnvelopeId("env_delivered_001"),
            destinationNodeId = dst,
            payloadCiphertextHash = PayloadCiphertextHash("hash_cipher_001"),
            receiptId = ReceiptId("rcpt_001"),
            deliveredAt = 1000L,
            createdAt = 1000L,
            expiresAt = 1000L + 86_400_000L // 24-hour retention
        )

        // Active before expiration
        assertFalse(tombstone.isExpiredAt(1000L + 3_600_000L))

        // Expired only after expiration timestamp is surpassed
        assertTrue(tombstone.isExpiredAt(1000L + 86_400_001L))
    }
}
