package spike.peerdirectory.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PeerDirectoryValueObjectsTest {

    @Test
    fun `HumanIdentityId validation and generation`() {
        val generated = HumanIdentityId.generate()
        assertTrue(generated.value.startsWith("human_"))

        val custom = HumanIdentityId("human_custom_123")
        assertEquals("human_custom_123", custom.value)

        assertThrows<IllegalArgumentException> {
            HumanIdentityId("   ")
        }
    }

    @Test
    fun `BindingId validation and generation`() {
        val generated = BindingId.generate()
        assertTrue(generated.value.startsWith("binding_"))

        val custom = BindingId("binding_custom_123")
        assertEquals("binding_custom_123", custom.value)

        assertThrows<IllegalArgumentException> {
            BindingId("")
        }
    }

    @Test
    fun `VerificationSessionId validation and generation`() {
        val generated = VerificationSessionId.generate()
        assertTrue(generated.value.startsWith("vsession_"))
    }
}
