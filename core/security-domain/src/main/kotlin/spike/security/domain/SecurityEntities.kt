package spike.security.domain

import spike.identity.domain.NodeId
import java.util.UUID

/**
 * Domain authorization context model per SPEC-009 Section 36.
 */
data class AuthorizationContext(
    val messageType: String? = null,
    val payloadBytes: Long? = null,
    val objectBytes: Long? = null,
    val mediaType: String? = null,
    val storageBytes: Long? = null,
    val applicationNamespace: String? = null,
    val currentConcurrentOperations: Int? = null,
    val encounterId: String? = null,
    val logicalLinkId: String? = null,
    val secureSessionId: String? = null,
    val localApplicationId: String? = null,
    val operationMetadata: Map<String, String> = emptyMap()
)

/**
 * Peer security profile record maintained per peer.
 */
data class PeerSecurityProfile(
    val peerNodeId: NodeId,
    val trustState: TrustState = TrustState.UNKNOWN,
    val trustVersion: TrustVersion = TrustVersion(1L),
    val revocationVersion: RevocationVersion = RevocationVersion(1L),
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val trustedAt: Long? = null,
    val blockedAt: Long? = null,
    val policyProfileId: String = "DEFAULT_UNKNOWN_PEER",
    val securityLabels: Set<String> = emptySet(),
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Explicit registry definition for a capability.
 */
data class CapabilityDefinition(
    val capabilityId: CapabilityId,
    val description: String,
    val resourceType: ResourceType,
    val operationType: OperationType,
    val allowedConstraintTypes: Set<String>,
    val defaultRiskLevel: String = "NORMAL",
    val requiresTrustedPeer: Boolean = false,
    val supportsOneShotGrant: Boolean = true,
    val supportsTemporaryGrant: Boolean = true,
    val supportsReservation: Boolean = true,
    val supportsUsageAccounting: Boolean = true,
    val createdAt: Long
)

/**
 * Capability Grant entity.
 */
data class CapabilityGrant(
    val grantId: GrantId = GrantId.generate(),
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId,
    val effect: GrantEffect = GrantEffect.ALLOW,
    val state: GrantState = GrantState.ACTIVE,
    val constraints: ConstraintSet = ConstraintSet(),
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val maxUses: Long? = null,
    val remainingUses: Long? = null,
    val createdBy: String = "LOCAL_SYSTEM",
    val createdAt: Long,
    val updatedAt: Long,
    val revokedAt: Long? = null,
    val revocationReason: RevocationReason? = null,
    val policyVersionCreatedUnder: PolicyVersion = PolicyVersion(1L)
) {
    init {
        maxUses?.let { require(it > 0) { "maxUses must be positive if specified" } }
        remainingUses?.let { require(it >= 0) { "remainingUses cannot be negative" } }
    }

    val isOneShot: Boolean get() = maxUses == 1L

    fun isExpiredAt(nowEpochMs: Long): Boolean {
        validUntil?.let { if (nowEpochMs > it) return true }
        constraints.findConstraint<ValidUntilConstraint>()?.let {
            if (nowEpochMs > it.validUntilEpochMs) return true
        }
        constraints.findConstraint<TimeWindowConstraint>()?.let {
            if (nowEpochMs > it.endTimeEpochMs) return true
        }
        return false
    }

    fun isExhausted(): Boolean {
        return remainingUses != null && remainingUses <= 0
    }
}

/**
 * Input request for an authorization decision.
 */
data class AuthorizationRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId,
    val operationType: OperationType,
    val resourceType: ResourceType,
    val resourceId: String? = null,
    val requestedUsage: Long = 1L,
    val context: AuthorizationContext = AuthorizationContext(),
    val requestedAt: Long
) {
    init {
        require(requestedUsage >= 0) { "requestedUsage cannot be negative" }
    }
}

/**
 * Result of an authorization evaluation.
 */
data class AuthorizationDecision(
    val decisionId: DecisionId = DecisionId.generate(),
    val requestId: String,
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId,
    val effect: AuthorizationEffect,
    val reasonCode: AuthorizationReason,
    val matchedGrantIds: List<GrantId> = emptyList(),
    val matchedDenyGrantIds: List<GrantId> = emptyList(),
    val evaluatedConstraints: List<String> = emptyList(),
    val policyId: PolicyId = PolicyId("DEFAULT"),
    val policyVersion: PolicyVersion = PolicyVersion(1L),
    val trustVersion: TrustVersion = TrustVersion(1L),
    val revocationVersion: RevocationVersion = RevocationVersion(1L),
    val decidedAt: Long,
    val expiresAt: Long? = null
)

/**
 * Active lease for long-running protected operations.
 */
data class AuthorizationLease(
    val leaseId: AuthorizationLeaseId = AuthorizationLeaseId.generate(),
    val decisionId: DecisionId,
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId,
    val resourceId: String? = null,
    val grantIds: List<GrantId> = emptyList(),
    val policyVersion: PolicyVersion,
    val trustVersion: TrustVersion,
    val revocationVersion: RevocationVersion,
    val state: LeaseState = LeaseState.ACTIVE,
    val issuedAt: Long,
    val expiresAt: Long,
    val lastValidatedAt: Long
)

/**
 * Active capacity/resource reservation.
 */
data class ResourceReservation(
    val reservationId: ReservationId = ReservationId.generate(),
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId,
    val resourceType: ResourceType,
    val resourceId: String? = null,
    val reservedUsage: Long,
    val consumedUsage: Long = 0L,
    val state: ReservationState = ReservationState.ACTIVE,
    val createdAt: Long,
    val expiresAt: Long,
    val completedAt: Long? = null,
    val releasedAt: Long? = null
) {
    val unconsumedReserved: Long get() = (reservedUsage - consumedUsage).coerceAtLeast(0L)
}

/**
 * Cumulative and window usage accounting counter.
 */
data class UsageCounter(
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId?,
    val usageDimension: UsageDimension,
    val periodStart: Long,
    val periodEnd: Long?,
    val consumed: Long,
    val reserved: Long,
    val limit: Long,
    val updatedAt: Long
) {
    val available: Long get() = (limit - consumed - reserved).coerceAtLeast(0L)
}

/**
 * Fixed Window Rate Limit Bucket.
 */
data class RateLimitBucket(
    val peerNodeId: NodeId,
    val capabilityId: CapabilityId,
    val dimension: UsageDimension,
    val windowStart: Long,
    val windowEnd: Long,
    val limit: Long,
    val consumed: Long,
    val reserved: Long,
    val updatedAt: Long
) {
    val available: Long get() = (limit - consumed - reserved).coerceAtLeast(0L)
}

/**
 * Consistent snapshot of policy state at decision time.
 */
data class PolicySnapshot(
    val policyId: PolicyId = PolicyId("DEFAULT"),
    val policyVersion: PolicyVersion = PolicyVersion(1L),
    val trustVersion: TrustVersion = TrustVersion(1L),
    val revocationVersion: RevocationVersion = RevocationVersion(1L),
    val grantSetVersion: Long = 1L,
    val quotaVersion: Long = 1L,
    val createdAt: Long
)

/**
 * Immutable security audit log event.
 */
data class SecurityAuditEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val peerNodeId: NodeId?,
    val capabilityId: CapabilityId?,
    val grantId: GrantId? = null,
    val decisionId: DecisionId? = null,
    val leaseId: AuthorizationLeaseId? = null,
    val reservationId: ReservationId? = null,
    val policyVersion: PolicyVersion = PolicyVersion(1L),
    val trustVersion: TrustVersion = TrustVersion(1L),
    val revocationVersion: RevocationVersion = RevocationVersion(1L),
    val reasonCode: AuthorizationReason? = null,
    val resourceType: ResourceType? = null,
    val resourceId: String? = null,
    val usageSummary: Map<String, String> = emptyMap(),
    val timestamp: Long
)

/**
 * Human/API-explainable decision output.
 */
data class AuthorizationExplanation(
    val decisionId: DecisionId,
    val effect: AuthorizationEffect,
    val reasonCode: AuthorizationReason,
    val peerTrustState: TrustState,
    val capabilityId: CapabilityId,
    val matchedAllowGrants: List<GrantId>,
    val matchedDenyGrants: List<GrantId>,
    val failedConstraints: List<String>,
    val rateLimitResult: String? = null,
    val quotaResult: String? = null,
    val concurrencyResult: String? = null,
    val policyVersion: PolicyVersion,
    val decidedAt: Long
)
