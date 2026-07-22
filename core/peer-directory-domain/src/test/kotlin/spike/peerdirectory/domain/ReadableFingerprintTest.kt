package spike.peerdirectory.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadableFingerprintTest {

    @Test
    fun `generateFingerprint derives deterministic raw base32 and 10 groups of 4 uppercase characters`() {
        val pubKeyData = "test-node-public-identity-key-material-0123456789".toByteArray(Charsets.UTF_8)

        val fp1 = ReadableFingerprintGenerator.generateFingerprint(pubKeyData)
        val fp2 = ReadableFingerprintGenerator.generateFingerprint(pubKeyData)

        assertEquals(fp1.raw, fp2.raw)
        assertEquals(fp1.formatted, fp2.formatted)

        // Raw should be lowercase base32
        assertTrue(fp1.raw.matches(Regex("^[a-z2-7]+$")))

        // Formatted should be 10 groups of 4 uppercase base32 characters separated by spaces
        val groups = fp1.formatted.split(" ")
        assertEquals(10, groups.size)
        groups.forEach { group ->
            assertEquals(4, group.length)
            assertTrue(group.matches(Regex("^[A-Z2-7]{4}$")), "Group $group must match 4 uppercase base32 characters")
        }
    }

    @Test
    fun `different public identity inputs produce different fingerprints`() {
        val data1 = "node-1-key-bytes".toByteArray(Charsets.UTF_8)
        val data2 = "node-2-key-bytes".toByteArray(Charsets.UTF_8)

        val fp1 = ReadableFingerprintGenerator.generateFingerprint(data1)
        val fp2 = ReadableFingerprintGenerator.generateFingerprint(data2)

        assertTrue(fp1.raw != fp2.raw)
        assertTrue(fp1.formatted != fp2.formatted)
    }
}
