package spike.identity.domain

sealed class IdentityCoreError(
    val code: String,
    message: String
) : Exception(message)

sealed class IdentityCreationError(code: String, message: String) : IdentityCoreError(code, message)
class KeyGenerationFailure(message: String) : IdentityCreationError("KeyGenerationFailure", message)
class KeyPersistenceFailure(message: String) : IdentityCreationError("KeyPersistenceFailure", message)
class IdentityDocumentCreationFailure(message: String) : IdentityCreationError("IdentityDocumentCreationFailure", message)
class IdentityActivationFailure(message: String) : IdentityCreationError("IdentityActivationFailure", message)

sealed class IdentityValidationError(code: String, message: String) : IdentityCoreError(code, message)
class MalformedIdentityDocument(message: String) : IdentityValidationError("MalformedIdentityDocument", message)
class UnsupportedIdentityVersion(version: Int) :
    IdentityValidationError("UnsupportedIdentityVersion", "Unsupported identity version: $version")
class UnsupportedKeyAlgorithm(algorithm: String) :
    IdentityValidationError("UnsupportedKeyAlgorithm", "Unsupported key algorithm: $algorithm")
class InvalidPublicKey(message: String) : IdentityValidationError("InvalidPublicKey", message)
class KeyIdMismatch : IdentityValidationError("KeyIdMismatch", "KeyId does not match public key")
class NodeIdPublicKeyMismatch : IdentityValidationError("NodeIdPublicKeyMismatch", "NodeId does not match public key")
class InvalidIdentityDocumentSignature : IdentityValidationError(
    "InvalidIdentityDocumentSignature",
    "Identity document signature is invalid"
)

sealed class SignatureError(code: String, message: String) : IdentityCoreError(code, message)
class SigningFailure(message: String) : SignatureError("SigningFailure", message)
class InvalidSignature : SignatureError("InvalidSignature", "Signature is invalid")
class InvalidDomainTag(message: String) : SignatureError("InvalidDomainTag", message)
class PayloadCanonicalizationFailure(message: String) : SignatureError("PayloadCanonicalizationFailure", message)

sealed class ProofError(code: String, message: String) : IdentityCoreError(code, message)
class InvalidChallenge(message: String) : ProofError("InvalidChallenge", message)
class UnknownChallenge(challengeId: ChallengeId) : ProofError("UnknownChallenge", "Unknown challenge: ${challengeId.value}")
class ExpiredChallenge(challengeId: ChallengeId) : ProofError("ExpiredChallenge", "Expired challenge: ${challengeId.value}")
class ConsumedChallenge(challengeId: ChallengeId) : ProofError("ConsumedChallenge", "Consumed challenge: ${challengeId.value}")
class ProofContextMismatch : ProofError("ProofContextMismatch", "Proof context mismatch")
class ProofNodeMismatch : ProofError("ProofNodeMismatch", "Proof node mismatch")
class InvalidProofSignature : ProofError("InvalidProofSignature", "Proof signature is invalid")
class ReplayDetected : ProofError("ReplayDetected", "Proof replay detected")

sealed class KeyStorageError(code: String, message: String) : IdentityCoreError(code, message)
class PrivateKeyUnavailable(handle: PrivateKeyHandle) :
    KeyStorageError("PrivateKeyUnavailable", "Private key unavailable: ${handle.value}")
class KeyHandleInvalid(handle: PrivateKeyHandle) :
    KeyStorageError("KeyHandleInvalid", "Key handle invalid: ${handle.value}")
class SecureStorageUnavailable(message: String) : KeyStorageError("SecureStorageUnavailable", message)
class KeyProtectionUnknown(message: String) : KeyStorageError("KeyProtectionUnknown", message)

sealed class IdentityStateError(code: String, message: String) : IdentityCoreError(code, message)
class IdentityNotFound : IdentityStateError("IdentityNotFound", "Identity not found")
class IdentityNotActive(state: LocalIdentityState) : IdentityStateError("IdentityNotActive", "Identity is not active: $state")
class IdentityCompromised : IdentityStateError("IdentityCompromised", "Identity is compromised")
class IdentityRetired : IdentityStateError("IdentityRetired", "Identity is retired")
class InvalidIdentityStateTransition(from: LocalIdentityState, to: LocalIdentityState) :
    IdentityStateError("InvalidIdentityStateTransition", "Invalid identity state transition: $from -> $to")

sealed class IdentityRecoveryError(code: String, message: String) : IdentityCoreError(code, message)
class IncompleteIdentityCreation : IdentityRecoveryError("IncompleteIdentityCreation", "Incomplete identity creation")
class OrphanKeyDetected : IdentityRecoveryError("OrphanKeyDetected", "Orphan key detected")
class MetadataKeyMismatch : IdentityRecoveryError("MetadataKeyMismatch", "Metadata and key mismatch")
class UnrecoverableIdentityState : IdentityRecoveryError("UnrecoverableIdentityState", "Unrecoverable identity state")
