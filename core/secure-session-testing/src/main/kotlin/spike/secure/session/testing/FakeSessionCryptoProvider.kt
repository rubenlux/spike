package spike.secure.session.testing

import spike.crypto.EphemeralKeyPair
import spike.crypto.EphemeralPrivateKey
import spike.crypto.EphemeralPublicKey
import spike.crypto.SessionCryptoProvider
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic fake SessionCryptoProvider for testing.
 *
 * - X25519 DH is simulated by a multiplicative scalar mix of the two key scalars
 *   (NOT real Diffie-Hellman — only for testing determinism).
 * - HKDF is the standard RFC 5869 construction using HMAC-SHA-256.
 * - AEAD is AES-GCM-256 as a stand-in for ChaCha20-Poly1305 (same interface contract).
 * - sha256 and hmacSha256 use JDK standard library.
 * - secureRandomBytes counter-based so tests can verify exact nonce bytes.
 */
class FakeSessionCryptoProvider(
    private val randomSeed: Byte = 0x42
) : SessionCryptoProvider {

    private var randomCounter: Int = 0

    override fun generateEphemeralKeyPair(): EphemeralKeyPair {
        // Deterministic: private key is counter-derived, public key is private XOR 0xAA
        val priv = ByteArray(32) { i -> (randomSeed + randomCounter + i).toByte() }
        randomCounter++
        val pub = ByteArray(32) { i -> (priv[i].toInt() xor 0xAA).toByte() }
        return EphemeralKeyPair(EphemeralPublicKey(pub), EphemeralPrivateKey(priv))
    }

    override fun calculateSharedSecret(
        localPrivateKey: EphemeralPrivateKey,
        remotePublicKey: EphemeralPublicKey
    ): ByteArray {
        // Simulated DH: XOR private with remote public, then SHA-256 to get uniform output
        val mixed = ByteArray(32) { i ->
            (localPrivateKey.bytes[i].toInt() xor remotePublicKey.bytes[i].toInt()).toByte()
        }
        // Make sure result is non-zero (avoid the all-zero guard in the impl)
        if (mixed.all { it == 0.toByte() }) mixed[0] = 1
        return sha256(mixed)
    }

    override fun hkdfExtract(salt: ByteArray, IKM: ByteArray): ByteArray {
        return hmacSha256(salt, IKM)
    }

    override fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val result = ByteArrayBuilder()
        var t = ByteArray(0)
        var counter = 1
        while (result.size < length) {
            t = hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            result.append(t)
            counter++
        }
        return result.toByteArray().copyOf(length)
    }

    override fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    override fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        // For testing: XOR cipher with key stream derived from HMAC(key, nonce || aad)
        // + 16-byte MAC = HMAC(key, ciphertext || aad)
        val keyStream = hkdfExpand(key, nonce + aad, plaintext.size)
        val ciphertext = ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor keyStream[i].toInt()).toByte() }
        val mac = hmacSha256(key, ciphertext + aad).copyOf(16)
        return ciphertext + mac
    }

    override fun aeadDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        if (ciphertext.size < 16) throw IllegalArgumentException("Ciphertext too short")
        val body = ciphertext.copyOf(ciphertext.size - 16)
        val tag = ciphertext.copyOfRange(ciphertext.size - 16, ciphertext.size)
        val expectedTag = hmacSha256(key, body + aad).copyOf(16)
        if (!tag.contentEquals(expectedTag)) {
            throw javax.crypto.AEADBadTagException("AEAD authentication tag mismatch")
        }
        val keyStream = hkdfExpand(key, nonce + aad, body.size)
        return ByteArray(body.size) { i -> (body[i].toInt() xor keyStream[i].toInt()).toByte() }
    }

    override fun secureRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length) { i -> (randomSeed + randomCounter + i).toByte() }
        randomCounter++
        return bytes
    }

    override fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    fun resetCounter() { randomCounter = 0 }

    private class ByteArrayBuilder {
        private val chunks = mutableListOf<ByteArray>()
        var size = 0
            private set

        fun append(bytes: ByteArray) {
            chunks += bytes
            size += bytes.size
        }

        fun toByteArray(): ByteArray {
            val result = ByteArray(size)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        }
    }
}
