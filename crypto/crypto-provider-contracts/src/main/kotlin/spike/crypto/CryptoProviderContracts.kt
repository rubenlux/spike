package spike.crypto

import spike.identity.domain.DomainTag
import spike.identity.domain.KeyAlgorithm
import spike.identity.domain.KeyProtectionLevel
import spike.identity.domain.PrivateKeyHandle
import spike.identity.domain.PublicKeyBytes
import spike.identity.domain.SignatureAlgorithm
import spike.identity.domain.SignatureBytes

data class GeneratedIdentityKeyPair(
    val keyAlgorithm: KeyAlgorithm,
    val signatureAlgorithm: SignatureAlgorithm,
    val publicKey: PublicKeyBytes,
    val privateKeyHandle: PrivateKeyHandle,
    val protectionLevel: KeyProtectionLevel
)

interface IdentityCryptoProvider {
    suspend fun generateIdentityKeyPair(): GeneratedIdentityKeyPair

    suspend fun sign(
        privateKeyHandle: PrivateKeyHandle,
        domainTag: DomainTag,
        canonicalPayload: ByteArray
    ): SignatureBytes

    suspend fun verify(
        publicKey: PublicKeyBytes,
        domainTag: DomainTag,
        canonicalPayload: ByteArray,
        signature: SignatureBytes
    ): Boolean

    suspend fun privateKeyAvailable(privateKeyHandle: PrivateKeyHandle): Boolean

    fun secureRandomBytes(length: Int): ByteArray

    fun sha256(bytes: ByteArray): ByteArray
}

interface CanonicalIdentityEncoder {
    fun encodeIdentityDocumentUnsigned(document: spike.identity.domain.IdentityDocument): ByteArray

    fun encodeProofUnsigned(proof: spike.identity.domain.IdentityProof): ByteArray

    fun encodeApplicationPayload(payload: ByteArray): ByteArray
}
