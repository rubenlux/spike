package spike.identity.domain

const val IDENTITY_DOCUMENT_VERSION = 1
const val PROOF_VERSION = 1

enum class LocalIdentityState {
    CREATING,
    ACTIVE,
    INVALID,
    KEY_UNAVAILABLE,
    COMPROMISED,
    RETIRED
}

enum class RemoteIdentityValidationState {
    UNVALIDATED,
    VALID,
    INVALID,
    UNSUPPORTED
}

enum class ChallengeState {
    ISSUED,
    CONSUMED,
    EXPIRED
}

enum class KeyProtectionLevel {
    SOFTWARE,
    TEE,
    STRONGBOX,
    UNKNOWN
}

data class IdentityDocument(
    val identityVersion: IdentityVersion,
    val nodeId: NodeId,
    val keyId: KeyId,
    val keyAlgorithm: KeyAlgorithm,
    val signatureAlgorithm: SignatureAlgorithm,
    val publicKey: PublicKeyBytes,
    val createdAtEpochMillis: Long,
    val capabilities: Map<String, String>,
    val documentSignature: SignatureBytes?
)

data class LocalNodeIdentity(
    val nodeId: NodeId,
    val keyId: KeyId,
    val identityVersion: IdentityVersion,
    val keyAlgorithm: KeyAlgorithm,
    val publicKey: PublicKeyBytes,
    val privateKeyHandle: PrivateKeyHandle,
    val identityDocument: IdentityDocument,
    val state: LocalIdentityState,
    val createdAtEpochMillis: Long,
    val protectionLevel: KeyProtectionLevel
)

data class RemoteNodeIdentity(
    val nodeId: NodeId,
    val keyId: KeyId,
    val publicKey: PublicKeyBytes,
    val identityDocument: IdentityDocument,
    val validationState: RemoteIdentityValidationState,
    val firstSeenAtEpochMillis: Long,
    val lastValidatedAtEpochMillis: Long?
)

data class Challenge(
    val challengeId: ChallengeId,
    val challengeHash: Sha256Digest,
    val randomBytes: ChallengeBytes,
    val claimantNodeId: NodeId,
    val context: ChallengeContext,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val state: ChallengeState
)

data class IdentityProof(
    val proofVersion: Int,
    val challengeId: ChallengeId,
    val challenge: ChallengeBytes,
    val verifierNodeId: NodeId,
    val claimantNodeId: NodeId,
    val context: ChallengeContext,
    val createdAtEpochMillis: Long,
    val signature: SignatureBytes
)

enum class IdentityEventType {
    IDENTITY_CREATION_STARTED,
    IDENTITY_KEY_GENERATED,
    IDENTITY_KEY_PERSISTED,
    IDENTITY_DOCUMENT_CREATED,
    IDENTITY_DOCUMENT_SIGNED,
    IDENTITY_VALIDATED,
    IDENTITY_ACTIVATED,
    IDENTITY_STARTUP_VALIDATED,
    IDENTITY_KEY_UNAVAILABLE,
    IDENTITY_INVALID,
    IDENTITY_MARKED_COMPROMISED,
    IDENTITY_RETIRED,
    REMOTE_IDENTITY_RECEIVED,
    REMOTE_IDENTITY_VALIDATED,
    REMOTE_IDENTITY_REJECTED,
    PROOF_CHALLENGE_CREATED,
    PROOF_CREATED,
    PROOF_VALIDATED,
    PROOF_REJECTED,
    PROOF_CHALLENGE_EXPIRED,
    PROOF_REPLAY_REJECTED
}

data class IdentityEvent(
    val eventId: String,
    val eventType: IdentityEventType,
    val localNodeId: NodeId?,
    val remoteNodeId: NodeId?,
    val keyId: KeyId?,
    val occurredAtEpochMillis: Long,
    val sequenceNumber: Long,
    val metadata: Map<String, String>
)
