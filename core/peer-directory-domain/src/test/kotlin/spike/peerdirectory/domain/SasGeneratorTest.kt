package spike.peerdirectory.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SasGeneratorTest {

    @Test
    fun `generateSas generates 6 decimal digits and 3 words deterministically`() {
        val sessionBindingHash = "session_binding_hash_0123456789abcdef"
        val transcriptHash = "transcript_hash_9876543210fedcba"

        val sas1 = SasGenerator.generateSas(sessionBindingHash, transcriptHash)
        val sas2 = SasGenerator.generateSas(sessionBindingHash, transcriptHash)

        assertEquals(sas1.numericCode, sas2.numericCode)
        assertEquals(sas1.words, sas2.words)

        // Numeric code must be 6 decimal digits
        assertEquals(6, sas1.numericCode.length)
        assertTrue(sas1.numericCode.matches(Regex("^[0-9]{6}$")))

        // Words must contain 3 dictionary words
        assertEquals(3, sas1.words.size)
        sas1.words.forEach { word ->
            assertTrue(SasGenerator.WORD_LIST.contains(word), "Word $word must be in dictionary")
        }
    }

    @Test
    fun `different binding hashes generate different SAS values`() {
        val sas1 = SasGenerator.generateSas("binding_hash_A")
        val sas2 = SasGenerator.generateSas("binding_hash_B")

        assertTrue(sas1.numericCode != sas2.numericCode || sas1.words != sas2.words)
    }
}
