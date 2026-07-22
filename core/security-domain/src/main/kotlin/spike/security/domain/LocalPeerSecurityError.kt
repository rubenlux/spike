package spike.security.domain

import spike.identity.domain.NodeId

/**
 * Root exception hierarchy for all LocalPeerSecurity domain errors.
 */
sealed class LocalPeerSecurityError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    sealed class PeerSecurityError(message: String) : LocalPeerSecurityError(message) {
        data class ProfileNotFound(val peerNodeId: NodeId) : PeerSecurityError("Peer security profile not found for node: ${peerNodeId.value}")
        data class InvalidTrustTransition(val from: TrustState, val to: TrustState) : PeerSecurityError("Invalid trust transition from $from to $to")
        data class PeerBlocked(val peerNodeId: NodeId) : PeerSecurityError("Peer ${peerNodeId.value} is blocked")
        data class PeerNotTrusted(val peerNodeId: NodeId) : PeerSecurityError("Peer ${peerNodeId.value} is not trusted")
        data class PeerSecurityStateConflict(val detail: String) : PeerSecurityError("Security state conflict: $detail")
    }

    sealed class CapabilityError(message: String) : LocalPeerSecurityError(message) {
        data class CapabilityUnknown(val capabilityId: CapabilityId) : CapabilityError("Unknown capability: $capabilityId")
        data class CapabilityAlreadyRegistered(val capabilityId: CapabilityId) : CapabilityError("Capability already registered: $capabilityId")
        data class InvalidCapabilityDefinition(val detail: String) : CapabilityError("Invalid capability definition: $detail")
        data class CapabilityConstraintUnsupported(val detail: String) : CapabilityError("Unsupported constraint: $detail")
    }

    sealed class GrantError(message: String) : LocalPeerSecurityError(message) {
        data class GrantNotFound(val grantId: GrantId) : GrantError("Grant not found: ${grantId.value}")
        data class InvalidGrant(val detail: String) : GrantError("Invalid grant: $detail")
        data class GrantExpired(val grantId: GrantId) : GrantError("Grant expired: ${grantId.value}")
        data class GrantExhausted(val grantId: GrantId) : GrantError("Grant usage exhausted: ${grantId.value}")
        data class GrantRevoked(val grantId: GrantId) : GrantError("Grant revoked: ${grantId.value}")
        data class GrantStateConflict(val detail: String) : GrantError("Grant state conflict: $detail")
        data class GrantAtomicConsumptionFailure(val detail: String) : GrantError("Atomic grant consumption failed: $detail")
    }

    sealed class AuthorizationError(message: String) : LocalPeerSecurityError(message) {
        data class AuthorizationDenied(val decision: AuthorizationDecision) : AuthorizationError("Authorization denied: ${decision.reasonCode}")
        data class InvalidAuthorizationRequest(val detail: String) : AuthorizationError("Invalid authorization request: $detail")
        data class AuthorizationContextIncomplete(val detail: String) : AuthorizationError("Incomplete authorization context: $detail")
        data class AuthorizationPeerMismatch(val detail: String) : AuthorizationError("Peer mismatch in authorization: $detail")
        data class AuthorizationPolicyUnavailable(val detail: String) : AuthorizationError("Security policy unavailable")
        data class AuthorizationStateConflict(val detail: String) : AuthorizationError("Authorization state conflict: $detail")
        data class AuthorizationDecisionPersistenceFailure(val detail: String) : AuthorizationError("Failed to persist decision: $detail")
    }

    sealed class ConstraintError(message: String) : LocalPeerSecurityError(message) {
        data class ConstraintEvaluationFailure(val detail: String) : ConstraintError("Constraint evaluation failed: $detail")
        data class MessageTypeConstraintViolation(val allowed: Set<String>, val actual: String) : ConstraintError("Message type '$actual' not allowed. Allowed: $allowed")
        data class PayloadSizeConstraintViolation(val maxBytes: Long, val actualBytes: Long) : ConstraintError("Payload size $actualBytes exceeds max allowed $maxBytes")
        data class ObjectSizeConstraintViolation(val maxBytes: Long, val actualBytes: Long) : ConstraintError("Object size $actualBytes exceeds max allowed $maxBytes")
        data class MediaTypeConstraintViolation(val allowed: Set<String>, val actual: String) : ConstraintError("Media type '$actual' not allowed. Allowed: $allowed")
        data class TimeWindowConstraintViolation(val startTime: Long, val endTime: Long, val actualTime: Long) : ConstraintError("Current time $actualTime outside window [$startTime, $endTime]")
        data class EncounterConstraintViolation(val expected: String, val actual: String) : ConstraintError("Encounter mismatch: expected $expected, actual $actual")
        data class ResourceScopeConstraintViolation(val expected: String, val actual: String) : ConstraintError("Resource scope mismatch: expected $expected, actual $actual")
        data class ApplicationNamespaceConstraintViolation(val allowed: Set<String>, val actual: String) : ConstraintError("Namespace '$actual' not allowed. Allowed: $allowed")
    }

    sealed class ReservationError(message: String) : LocalPeerSecurityError(message) {
        data class ReservationCapacityUnavailable(val detail: String) : ReservationError("Reservation capacity unavailable: $detail")
        data class ReservationNotFound(val reservationId: ReservationId) : ReservationError("Reservation not found: ${reservationId.value}")
        data class ReservationExpired(val reservationId: ReservationId) : ReservationError("Reservation expired: ${reservationId.value}")
        data class ReservationAlreadyConsumed(val reservationId: ReservationId) : ReservationError("Reservation already consumed: ${reservationId.value}")
        data class ReservationUsageExceeded(val reserved: Long, val attempted: Long) : ReservationError("Usage $attempted exceeds reserved capacity $reserved")
        data class ReservationStateConflict(val detail: String) : ReservationError("Reservation state conflict: $detail")
    }

    sealed class RateLimitError(message: String) : LocalPeerSecurityError(message) {
        data class RateLimitExceeded(val dimension: UsageDimension, val limit: Long, val requested: Long) : RateLimitError("Rate limit exceeded for $dimension: limit $limit, requested $requested")
        data class RateLimitStateConflict(val detail: String) : RateLimitError("Rate limit state conflict: $detail")
        data class RateLimitReservationFailure(val detail: String) : RateLimitError("Rate limit reservation failed: $detail")
        data class RateLimitRecoveryFailure(val detail: String) : RateLimitError("Rate limit recovery failed: $detail")
    }

    sealed class QuotaError(message: String) : LocalPeerSecurityError(message) {
        data class QuotaExceeded(val dimension: UsageDimension, val limit: Long, val requested: Long) : QuotaError("Quota exceeded for $dimension: limit $limit, requested $requested")
        data class QuotaReservationFailure(val detail: String) : QuotaError("Quota reservation failed: $detail")
        data class QuotaAccountingFailure(val detail: String) : QuotaError("Quota accounting failed: $detail")
        data class QuotaRecoveryFailure(val detail: String) : QuotaError("Quota recovery failed: $detail")
    }

    sealed class LeaseError(message: String) : LocalPeerSecurityError(message) {
        data class AuthorizationLeaseNotFound(val leaseId: AuthorizationLeaseId) : LeaseError("Authorization lease not found: ${leaseId.value}")
        data class AuthorizationLeaseExpired(val leaseId: AuthorizationLeaseId) : LeaseError("Authorization lease expired: ${leaseId.value}")
        data class AuthorizationLeaseRevoked(val leaseId: AuthorizationLeaseId) : LeaseError("Authorization lease revoked: ${leaseId.value}")
        data class AuthorizationLeasePeerMismatch(val expectedPeer: NodeId, val actualPeer: NodeId) : LeaseError("Lease peer mismatch: expected ${expectedPeer.value}, actual ${actualPeer.value}")
        data class AuthorizationLeasePolicyConflict(val detail: String) : LeaseError("Lease policy conflict: $detail")
        data class AuthorizationLeaseValidationFailure(val detail: String) : LeaseError("Lease validation failed: $detail")
    }

    sealed class RevocationError(message: String) : LocalPeerSecurityError(message) {
        data class RevocationConflict(val detail: String) : RevocationError("Revocation conflict: $detail")
        data class ActiveOperationRevocationFailure(val detail: String) : RevocationError("Failed to revoke active operation: $detail")
        data class RevocationVersionConflict(val expectedVersion: RevocationVersion, val actualVersion: RevocationVersion) : RevocationError("Revocation version mismatch: expected ${expectedVersion.value}, actual ${actualVersion.value}")
        data class RevocationPersistenceFailure(val detail: String) : RevocationError("Failed to persist revocation: $detail")
    }

    sealed class RecoveryError(message: String) : LocalPeerSecurityError(message) {
        data class SecurityRecoveryFailure(val detail: String) : RecoveryError("Security recovery failure: $detail")
        data class OrphanReservation(val reservationId: ReservationId) : RecoveryError("Orphan reservation detected: ${reservationId.value}")
        data class InvalidUsageCounter(val detail: String) : RecoveryError("Invalid usage counter: $detail")
        data class UsageAccountingMismatch(val detail: String) : RecoveryError("Usage accounting mismatch: $detail")
        data class StaleAuthorizationLease(val leaseId: AuthorizationLeaseId) : RecoveryError("Stale authorization lease: ${leaseId.value}")
        data class InvalidPolicySnapshot(val detail: String) : RecoveryError("Invalid policy snapshot: $detail")
        data class SecurityStateInconsistency(val detail: String) : RecoveryError("Security state inconsistency detected: $detail")
    }
}
