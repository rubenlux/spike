package spike.secure.session.domain

sealed class SecureSessionError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    sealed class NegotiationError(message: String) : SecureSessionError(message) {
        class UnsupportedSessionVersion(message: String) : NegotiationError(message)
        class NoCompatibleSecureSessionSuite(message: String) : NegotiationError(message)
        class InvalidSuiteSelection(message: String) : NegotiationError(message)
        class DowngradeDetected(message: String) : NegotiationError(message)
    }

    sealed class RoleError(message: String) : SecureSessionError(message) {
        class InvalidSessionRole(message: String) : RoleError(message)
        class RoleConflict(message: String) : RoleError(message)
        class DuplicateHandshakeConflict(message: String) : RoleError(message)
    }

    sealed class EphemeralKeyError(message: String) : SecureSessionError(message) {
        class EphemeralKeyGenerationFailure(message: String) : EphemeralKeyError(message)
        class InvalidEphemeralPublicKey(message: String) : EphemeralKeyError(message)
        class EphemeralKeyReuseDetected(message: String) : EphemeralKeyError(message)
        class InvalidSharedSecret(message: String) : EphemeralKeyError(message)
    }

    sealed class TranscriptError(message: String) : SecureSessionError(message) {
        class MalformedHandshakeTranscript(message: String) : TranscriptError(message)
        class TranscriptMismatch(message: String) : TranscriptError(message)
        class TranscriptHashMismatch(message: String) : TranscriptError(message)
        class HandshakeBindingFailure(message: String) : TranscriptError(message)
    }

    sealed class HandshakeAuthenticationError(message: String) : SecureSessionError(message) {
        class InvalidHandshakeIdentity(message: String) : HandshakeAuthenticationError(message)
        class InvalidHandshakeSignature(message: String) : HandshakeAuthenticationError(message)
        class HandshakeNodeMismatch(message: String) : HandshakeAuthenticationError(message)
        class HandshakeRoleMismatch(message: String) : HandshakeAuthenticationError(message)
        class UnknownKeyShareDetected(message: String) : HandshakeAuthenticationError(message)
        class ReflectionDetected(message: String) : HandshakeAuthenticationError(message)
    }

    sealed class KeyDerivationError(message: String) : SecureSessionError(message) {
        class HkdfFailure(message: String) : KeyDerivationError(message)
        class KeyScheduleFailure(message: String) : KeyDerivationError(message)
        class KeyConfirmationFailure(message: String) : KeyDerivationError(message)
        class InvalidDerivedKeyMaterial(message: String) : KeyDerivationError(message)
    }

    sealed class SecureRecordError(message: String, cause: Throwable? = null) : SecureSessionError(message, cause) {
        class SecureRecordTooLarge(message: String) : SecureRecordError(message)
        class MalformedSecureRecord(message: String, cause: Throwable? = null) : SecureRecordError(message, cause)
        class SessionIdMismatch(message: String) : SecureRecordError(message)
        class InvalidKeyEpoch(message: String) : SecureRecordError(message)
        class ReplayDetected(message: String) : SecureRecordError(message)
        class OutOfOrderRecord(message: String) : SecureRecordError(message)
        class SequenceOverflow(message: String) : SecureRecordError(message)
        class AeadAuthenticationFailure(message: String, cause: Throwable? = null) : SecureRecordError(message, cause)
        class DecryptionFailure(message: String) : SecureRecordError(message)
    }

    sealed class RekeyError(message: String) : SecureSessionError(message) {
        class RekeyThresholdExceeded(message: String) : RekeyError(message)
        class RekeyProtocolFailure(message: String) : RekeyError(message)
        class RekeyEpochMismatch(message: String) : RekeyError(message)
        class RekeyConfirmationFailure(message: String) : RekeyError(message)
        class RekeySynchronizationLost(message: String) : RekeyError(message)
    }

    sealed class SessionStateError(message: String) : SecureSessionError(message) {
        class SecureSessionNotFound(message: String) : SessionStateError(message)
        class SecureSessionNotEstablished(message: String) : SessionStateError(message)
        class SecureSessionAlreadyTerminal(message: String) : SessionStateError(message)
        class InvalidSecureSessionStateTransition(message: String) : SessionStateError(message)
    }

    sealed class SecretLifecycleError(message: String) : SecureSessionError(message) {
        class SecretDestructionFailure(message: String) : SecretLifecycleError(message)
        class SecretLifecycleInvariantViolation(message: String) : SecretLifecycleError(message)
    }
}
