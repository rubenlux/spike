package spike.peerdirectory.domain

import spike.identity.domain.NodeId

sealed class LocalPeerIdentityError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    sealed class PeerDirectoryError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class PeerDirectoryEntryNotFound(val peerNodeId: NodeId) : PeerDirectoryError("Peer directory entry not found for peer: ${peerNodeId.value}")
        data class PeerDirectoryEntryConflict(val peerNodeId: NodeId) : PeerDirectoryError("Peer directory entry conflict for peer: ${peerNodeId.value}")
        data class InvalidLocalAlias(val alias: String, val reason: String) : PeerDirectoryError("Invalid local alias '$alias': $reason")
        data class PeerDirectoryPersistenceFailure(val op: String, override val cause: Throwable) : PeerDirectoryError("Peer directory persistence failure during $op: ${cause.message}", cause)
    }

    sealed class HumanIdentityError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class HumanIdentityNotFound(val id: HumanIdentityId) : HumanIdentityError("Human identity not found: ${id.value}")
        data class HumanIdentityStateConflict(val id: HumanIdentityId, val state: HumanIdentityState) : HumanIdentityError("Human identity state conflict for ${id.value}: $state")
        data class HumanIdentityAlreadyArchived(val id: HumanIdentityId) : HumanIdentityError("Human identity already archived: ${id.value}")
        data class HumanIdentityPersistenceFailure(val op: String, override val cause: Throwable) : HumanIdentityError("Human identity persistence failure during $op: ${cause.message}", cause)
    }

    sealed class BindingError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class BindingNotFound(val bindingId: BindingId) : BindingError("Human node binding not found: ${bindingId.value}")
        data class BindingConflict(val details: String) : BindingError("Binding conflict: $details")
        data class PeerAlreadyActivelyBound(val peerNodeId: NodeId, val existingBindingId: BindingId) : BindingError("Peer ${peerNodeId.value} is already actively bound to ${existingBindingId.value}")
        data class BindingVerificationRequired(val peerNodeId: NodeId) : BindingError("Verification evidence is required to bind peer: ${peerNodeId.value}")
        data class BindingRevoked(val bindingId: BindingId) : BindingError("Binding is revoked: ${bindingId.value}")
        data class BindingPersistenceFailure(val op: String, override val cause: Throwable) : BindingError("Binding persistence failure during $op: ${cause.message}", cause)
    }

    sealed class FingerprintError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class FingerprintDerivationFailure(val details: String, override val cause: Throwable? = null) : FingerprintError("Fingerprint derivation failed: $details", cause)
        data class FingerprintMismatch(val expected: String, val actual: String) : FingerprintError("Fingerprint mismatch. Expected: $expected, Actual: $actual")
        data class FingerprintEncodingFailure(val details: String) : FingerprintError("Fingerprint encoding failed: $details")
        data class FingerprintFormatInvalid(val format: String) : FingerprintError("Fingerprint format invalid: $format")
    }

    sealed class VerificationError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class VerificationSessionNotFound(val id: VerificationSessionId) : VerificationError("Verification session not found: ${id.value}")
        data class VerificationSessionExpired(val id: VerificationSessionId) : VerificationError("Verification session expired: ${id.value}")
        data class VerificationSessionAlreadyCompleted(val id: VerificationSessionId) : VerificationError("Verification session already completed: ${id.value}")
        data class VerificationPeerMismatch(val expected: NodeId, val actual: NodeId) : VerificationError("Verification peer mismatch. Expected: ${expected.value}, Actual: ${actual.value}")
        data class VerificationMethodMismatch(val expected: VerificationMethod, val actual: VerificationMethod) : VerificationError("Verification method mismatch. Expected: $expected, Actual: $actual")
        data class VerificationSessionBindingMismatch(val details: String) : VerificationError("Verification session binding mismatch: $details")
        data class VerificationNonceReplay(val nonce: String) : VerificationError("Verification nonce replay detected: $nonce")
        data class VerificationAttemptLimitExceeded(val sessionId: VerificationSessionId) : VerificationError("Verification attempt limit exceeded for session: ${sessionId.value}")
        data class VerificationStateConflict(val sessionId: VerificationSessionId, val state: VerificationState) : VerificationError("Verification state conflict for session ${sessionId.value}: $state")
        data class VerificationPersistenceFailure(val op: String, override val cause: Throwable) : VerificationError("Verification persistence failure during $op: ${cause.message}", cause)
    }

    sealed class QrVerificationError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class QrPayloadMalformed(val details: String) : QrVerificationError("QR payload malformed: $details")
        data class QrPayloadExpired(val sessionId: VerificationSessionId) : QrVerificationError("QR payload expired for session: ${sessionId.value}")
        data class QrNodeIdMismatch(val expected: NodeId, val actual: NodeId) : QrVerificationError("QR NodeId mismatch. Expected: ${expected.value}, Actual: ${actual.value}")
        data class QrSessionMismatch(val expected: VerificationSessionId, val actual: VerificationSessionId) : QrVerificationError("QR Session mismatch. Expected: ${expected.value}, Actual: ${actual.value}")
        data class QrFingerprintMismatch(val details: String) : QrVerificationError("QR Fingerprint mismatch: $details")
        data class QrReplayDetected(val nonce: String) : QrVerificationError("QR Replay detected for nonce: $nonce")
    }

    sealed class SasVerificationError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class SasDerivationFailure(val details: String) : SasVerificationError("SAS derivation failure: $details")
        data class SasMismatch(val sessionId: VerificationSessionId) : SasVerificationError("SAS mismatch for session: ${sessionId.value}")
        data class SasConfirmationMissing(val sessionId: VerificationSessionId) : SasVerificationError("SAS confirmation missing for session: ${sessionId.value}")
        data class SasEntropyInsufficient(val details: String) : SasVerificationError("SAS entropy insufficient: $details")
        data class SasTranscriptMismatch(val details: String) : SasVerificationError("SAS transcript mismatch: $details")
    }

    sealed class TrustEstablishmentError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class TrustEvidenceMissing(val peerNodeId: NodeId) : TrustEstablishmentError("Trust evidence missing for peer: ${peerNodeId.value}")
        data class TrustEvidenceInvalid(val evidenceId: VerificationEvidenceId) : TrustEstablishmentError("Trust evidence invalid: ${evidenceId.value}")
        data class TrustOverrideConfirmationMissing(val peerNodeId: NodeId) : TrustEstablishmentError("Trust override confirmation missing for peer: ${peerNodeId.value}")
        data class TrustStateUpdateFailure(val peerNodeId: NodeId, override val cause: Throwable) : TrustEstablishmentError("Trust state update failure for peer: ${peerNodeId.value}", cause)
        data class TrustEstablishmentConflict(val details: String) : TrustEstablishmentError("Trust establishment conflict: $details")
    }

    sealed class DeviceReplacementError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class ReplacementOperationNotFound(val id: ReplacementOperationId) : DeviceReplacementError("Device replacement operation not found: ${id.value}")
        data class NewDeviceVerificationRequired(val newPeerNodeId: NodeId) : DeviceReplacementError("Verification required for new device: ${newPeerNodeId.value}")
        data class ReplacementIdentityMismatch(val details: String) : DeviceReplacementError("Replacement identity mismatch: $details")
        data class ReplacementStateConflict(val opId: ReplacementOperationId, val state: DeviceReplacementState) : DeviceReplacementError("Device replacement state conflict for ${opId.value}: $state")
        data class TrustMigrationNotConfirmed(val planId: TrustMigrationPlanId) : DeviceReplacementError("Trust migration not confirmed for plan: ${planId.value}")
        data class DeviceReplacementPersistenceFailure(val op: String, override val cause: Throwable) : DeviceReplacementError("Device replacement persistence failure during $op: ${cause.message}", cause)
    }

    sealed class TrustMigrationError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class TrustMigrationPlanNotFound(val id: TrustMigrationPlanId) : TrustMigrationError("Trust migration plan not found: ${id.value}")
        data class GrantNotMigratable(val grantId: String, val reason: String) : TrustMigrationError("Grant $grantId is not migratable: $reason")
        data class TrustMigrationConfirmationMissing(val id: TrustMigrationPlanId) : TrustMigrationError("Trust migration confirmation missing for plan: ${id.value}")
        data class TrustMigrationConflict(val details: String) : TrustMigrationError("Trust migration conflict: $details")
        data class TrustMigrationApplicationFailure(val op: String, override val cause: Throwable) : TrustMigrationError("Trust migration application failure during $op: ${cause.message}", cause)
    }

    sealed class RecoveryError(message: String, cause: Throwable? = null) : LocalPeerIdentityError(message, cause) {
        data class PeerIdentityRecoveryFailure(val details: String, override val cause: Throwable? = null) : RecoveryError("Peer identity recovery failed: $details", cause)
        data class StaleVerificationSession(val id: VerificationSessionId) : RecoveryError("Stale verification session detected: ${id.value}")
        data class OrphanVerificationEvidence(val id: VerificationEvidenceId) : RecoveryError("Orphan verification evidence detected: ${id.value}")
        data class BindingHistoryMismatch(val details: String) : RecoveryError("Binding history mismatch: $details")
        data class ReplacementOperationInconsistent(val opId: ReplacementOperationId) : RecoveryError("Replacement operation inconsistent: ${opId.value}")
        data class PeerIdentityStateInconsistency(val details: String) : RecoveryError("Peer identity state inconsistency: $details")
    }
}
