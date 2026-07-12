package spike.crypto

data class EphemeralPublicKey(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "X25519 public key must be 32 bytes" }
    }

    override fun equals(other: Any?): Boolean =
        other is EphemeralPublicKey && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

data class EphemeralPrivateKey(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "X25519 private key must be 32 bytes" }
    }

    override fun equals(other: Any?): Boolean =
        other is EphemeralPrivateKey && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    fun destroy() {
        bytes.fill(0)
    }
}

data class EphemeralKeyPair(
    val publicKey: EphemeralPublicKey,
    val privateKey: EphemeralPrivateKey
)

interface SessionCryptoProvider {
    fun generateEphemeralKeyPair(): EphemeralKeyPair

    fun calculateSharedSecret(
        localPrivateKey: EphemeralPrivateKey,
        remotePublicKey: EphemeralPublicKey
    ): ByteArray

    fun hkdfExtract(salt: ByteArray, IKM: ByteArray): ByteArray

    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    fun aeadEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray

    fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray

    fun secureRandomBytes(length: Int): ByteArray

    fun sha256(data: ByteArray): ByteArray
}
