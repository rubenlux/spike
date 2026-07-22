package spike.dtnrouting.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DtnRoutingValueObjectsTest {

    @Test
    fun `HopCount is monotonic and non-decreasing`() {
        val h1 = HopCount(2)
        val h2 = h1.next()
        assertEquals(3, h2.value)

        // Monotonicity assertion: ensureMonotonic never decreases
        val lower = HopCount(1)
        val reconciled = lower.ensureMonotonic(h2)
        assertEquals(3, reconciled.value)
    }

    @Test
    fun `HopCount rejects negative values`() {
        assertThrows<IllegalArgumentException> {
            HopCount(-1)
        }
    }

    @Test
    fun `CopyBudget binary spray split preserves copy budget invariant`() {
        val b8 = CopyBudget(8)
        val (rem8, trans8) = b8.split()
        assertEquals(4, rem8.value)
        assertEquals(4, trans8.value)
        assertEquals(8, rem8.value + trans8.value)

        val b5 = CopyBudget(5)
        val (rem5, trans5) = b5.split()
        assertEquals(3, rem5.value)
        assertEquals(2, trans5.value)
        assertEquals(5, rem5.value + trans5.value)

        val b1 = CopyBudget(1)
        val (rem1, trans1) = b1.split()
        assertEquals(1, rem1.value)
        assertEquals(0, trans1.value)
        assertEquals(1, rem1.value + trans1.value)
    }

    @Test
    fun `PredictabilityScore age decays score in range 0 to 1`() {
        val score = PredictabilityScore(0.8)
        val aged = score.age(0.5)
        assertEquals(0.4, aged.value, 0.0001)
    }

    @Test
    fun `PredictabilityScore rejects out-of-bounds values`() {
        assertThrows<IllegalArgumentException> {
            PredictabilityScore(1.2)
        }
        assertThrows<IllegalArgumentException> {
            PredictabilityScore(-0.1)
        }
    }
}
