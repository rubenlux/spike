package spike.security.domain

import spike.identity.domain.NodeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecurityValueObjectsTest {

    private val testNodeId = NodeId("node:v1:sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    @Test
    fun `CapabilityId valid format parses correctly`() {
        val cap = CapabilityId("messaging.message.receive")
        assertEquals("messaging", cap.namespace)
        assertEquals("message", cap.resource)
        assertEquals("receive", cap.action)
        assertEquals("messaging.message.receive", cap.raw)
    }

    @Test
    fun `CapabilityId nested resource parses correctly`() {
        val cap = CapabilityId("object.transfer.resume.receive")
        assertEquals("object", cap.namespace)
        assertEquals("transfer.resume", cap.resource)
        assertEquals("receive", cap.action)
    }

    @Test
    fun `CapabilityId invalid format throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { CapabilityId("") }
        assertThrows<IllegalArgumentException> { CapabilityId("messaging") }
        assertThrows<IllegalArgumentException> { CapabilityId("messaging.receive") }
        assertThrows<IllegalArgumentException> { CapabilityId("messaging..receive") }
    }

    @Test
    fun `CapabilityGrant expiration and usage evaluation`() {
        val capId = CapabilityId("messaging.message.receive")
        val grant = CapabilityGrant(
            peerNodeId = testNodeId,
            capabilityId = capId,
            validUntil = 1000L,
            maxUses = 5L,
            remainingUses = 5L,
            createdAt = 100L,
            updatedAt = 100L
        )

        assertFalse(grant.isExpiredAt(500L))
        assertTrue(grant.isExpiredAt(1001L))
        assertFalse(grant.isExhausted())

        val exhausted = grant.copy(remainingUses = 0L)
        assertTrue(exhausted.isExhausted())
    }
}
