package spike.peerdirectory.domain

import java.security.MessageDigest

object ReadableFingerprintGenerator {
    private const val DOMAIN_TAG = "OFFLINE_PROTOCOL_READABLE_FINGERPRINT_V1"
    private const val BASE32_ALPHABET_LOWER = "abcdefghijklmnopqrstuvwxyz234567"
    private const val BASE32_ALPHABET_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateFingerprint(canonicalNodeIdentityPublicData: ByteArray): ReadableFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN_TAG.toByteArray(Charsets.UTF_8))
        digest.update(canonicalNodeIdentityPublicData)
        val hash = digest.digest()

        val rawBase32Lower = encodeBase32(hash, BASE32_ALPHABET_LOWER)
        val base32Upper = encodeBase32(hash, BASE32_ALPHABET_UPPER)

        val first40 = base32Upper.take(40)
        val formatted = first40.chunked(4).joinToString(" ")

        return ReadableFingerprint(
            raw = rawBase32Lower,
            formatted = formatted
        )
    }

    private fun encodeBase32(data: ByteArray, alphabet: String): String {
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0

        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                result.append(alphabet[index])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            result.append(alphabet[index])
        }
        return result.toString()
    }
}
