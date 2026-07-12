package spike.identity.domain

private val NODE_ID = Regex("^node:v[0-9]+:sha256:[0-9a-f]{64}$")
private val KEY_ID = Regex("^sha256:[0-9a-f]{64}$")
private val LOWER_SHA256_HEX = Regex("^[0-9a-f]{64}$")

data class NodeId(val value: String) {
    init {
        require(NODE_ID.matches(value)) { "NodeId must be node:vN:sha256:<64 lowercase hex>" }
    }
}

data class KeyId(val value: String) {
    init {
        require(KEY_ID.matches(value)) { "KeyId must be sha256:<64 lowercase hex>" }
    }
}

data class IdentityVersion(val value: Int) {
    init {
        require(value > 0) { "IdentityVersion must be positive" }
    }
}

enum class KeyAlgorithm {
    ED25519
}

enum class SignatureAlgorithm {
    ED25519
}

enum class HashAlgorithm {
    SHA256
}

enum class DomainTag(val wireName: String) {
    IDENTITY_DOCUMENT_V1("OFFLINE_PROTOCOL_IDENTITY_DOCUMENT_V1"),
    IDENTITY_PROOF_V1("OFFLINE_PROTOCOL_IDENTITY_PROOF_V1"),
    APPLICATION_SIGNATURE_V1("OFFLINE_PROTOCOL_APPLICATION_SIGNATURE_V1"),
    SECURE_SESSION_AUTH_V1("OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1")
}

data class PublicKeyBytes(
    val algorithm: KeyAlgorithm,
    val bytes: ByteArray
) {
    init {
        require(bytes.isNotEmpty()) { "Public key bytes are required" }
    }

    override fun equals(other: Any?): Boolean =
        other is PublicKeyBytes && algorithm == other.algorithm && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * algorithm.hashCode() + bytes.contentHashCode()
}

data class SignatureBytes(
    val algorithm: SignatureAlgorithm,
    val bytes: ByteArray
) {
    init {
        require(bytes.isNotEmpty()) { "Signature bytes are required" }
    }

    override fun equals(other: Any?): Boolean =
        other is SignatureBytes && algorithm == other.algorithm && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * algorithm.hashCode() + bytes.contentHashCode()
}

data class PrivateKeyHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "PrivateKeyHandle is required" }
    }
}

data class ChallengeId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChallengeId is required" }
    }
}

data class ChallengeBytes(val bytes: ByteArray) {
    init {
        require(bytes.size >= 32) { "Challenge must contain at least 256 bits of entropy" }
    }

    override fun equals(other: Any?): Boolean =
        other is ChallengeBytes && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

data class ChallengeContext(val value: String) {
    init {
        require(value.isNotBlank()) { "Challenge context is required" }
        require(value.length <= 1024) { "Challenge context is too large" }
    }
}

data class Sha256Digest(val hex: String) {
    init {
        require(LOWER_SHA256_HEX.matches(hex)) { "SHA-256 digest must be 64 lowercase hex" }
    }
}
