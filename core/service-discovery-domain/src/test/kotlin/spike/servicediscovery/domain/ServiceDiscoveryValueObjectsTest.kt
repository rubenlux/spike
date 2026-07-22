package spike.servicediscovery.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ServiceDiscoveryValueObjectsTest {

    @Test
    fun `ContractVersion compatibility matching rule`() {
        val providerV12 = ContractVersion("1.2")
        val clientV10 = ContractVersion("1.0")
        val clientV12 = ContractVersion("1.2")
        val clientV13 = ContractVersion("1.3")
        val clientV20 = ContractVersion("2.0")

        assertTrue(providerV12.isCompatibleWith(clientV10))
        assertTrue(providerV12.isCompatibleWith(clientV12))
        assertFalse(providerV12.isCompatibleWith(clientV13))
        assertFalse(providerV12.isCompatibleWith(clientV20))
    }

    @Test
    fun `PayloadDescriptor invariant enforces exactly one payload type`() {
        assertThrows<IllegalArgumentException> {
            PayloadDescriptor(
                inlinePayload = "data".toByteArray(),
                objectReference = ObjectReference("obj1", "hash1", 100L),
                payloadHash = "hash",
                payloadLength = 4L
            )
        }
    }
}
