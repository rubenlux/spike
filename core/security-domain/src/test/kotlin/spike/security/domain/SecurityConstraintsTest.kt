package spike.security.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecurityConstraintsTest {

    @Test
    fun `MaximumPayloadBytesConstraint validates positive maxBytes`() {
        MaximumPayloadBytesConstraint(1024L)
        assertThrows<IllegalArgumentException> {
            MaximumPayloadBytesConstraint(0L)
        }
        assertThrows<IllegalArgumentException> {
            MaximumPayloadBytesConstraint(-10L)
        }
    }

    @Test
    fun `TimeWindowConstraint validates valid window`() {
        TimeWindowConstraint(100L, 200L)
        assertThrows<IllegalArgumentException> {
            TimeWindowConstraint(200L, 100L)
        }
    }

    @Test
    fun `ConstraintSet helpers find and inspect constraints`() {
        val msgType = MessageTypeConstraint(setOf("chat.text", "chat.image"))
        val maxPayload = MaximumPayloadBytesConstraint(5000L)
        val set = ConstraintSet(listOf(msgType, maxPayload))

        assertTrue(set.hasConstraintType("MessageTypeConstraint"))
        assertTrue(set.hasConstraintType("MaximumPayloadBytesConstraint"))
        assertFalse(set.hasConstraintType("EncounterConstraint"))

        val foundMsgType = set.findConstraint<MessageTypeConstraint>()
        assertEquals(setOf("chat.text", "chat.image"), foundMsgType?.allowedTypes)
    }
}
