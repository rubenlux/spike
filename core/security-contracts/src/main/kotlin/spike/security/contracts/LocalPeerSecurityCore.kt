package spike.security.contracts

import spike.identity.domain.NodeId
import spike.security.domain.AuthorizationContext
import spike.security.domain.AuthorizationDecision
import spike.security.domain.AuthorizationExplanation
import spike.security.domain.AuthorizationLease
import spike.security.domain.AuthorizationLeaseId
import spike.security.domain.AuthorizationRequest
import spike.security.domain.CapabilityDefinition
import spike.security.domain.CapabilityGrant
import spike.security.domain.CapabilityId
import spike.security.domain.ConstraintSet
import spike.security.domain.DecisionId
import spike.security.domain.GrantEffect
import spike.security.domain.GrantId
import spike.security.domain.PeerSecurityProfile
import spike.security.domain.RateLimitBucket
import spike.security.domain.ReservationId
import spike.security.domain.ResourceReservation
import spike.security.domain.RevocationReason
import spike.security.domain.SecurityAuditEvent
import spike.security.domain.TrustState
import spike.security.domain.UsageCounter

/** Listener interface for security audit event notifications. */
fun interface SecurityEventListener {
    fun onSecurityEvent(event: SecurityAuditEvent)
}

/**
 * Normative administrative and operational public API for LocalPeerSecurity per SPEC-009 Section 89.
 * Must contain interface declarations only with zero business logic.
 */
interface LocalPeerSecurityCore {
    suspend fun observePeer(peerNodeId: NodeId): PeerSecurityProfile

    suspend fun getPeerSecurityProfile(peerNodeId: NodeId): PeerSecurityProfile?

    suspend fun setTrustState(peerNodeId: NodeId, trustState: TrustState, reason: String): PeerSecurityProfile

    suspend fun trustPeer(peerNodeId: NodeId): PeerSecurityProfile

    suspend fun restrictPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile

    suspend fun removeTrust(peerNodeId: NodeId, reason: String): PeerSecurityProfile

    suspend fun blockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile

    suspend fun unblockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile

    suspend fun registerCapability(definition: CapabilityDefinition)

    suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition?

    suspend fun listCapabilities(): List<CapabilityDefinition>

    suspend fun createGrant(
        peerNodeId: NodeId,
        capabilityId: CapabilityId,
        constraints: ConstraintSet = ConstraintSet(),
        effect: GrantEffect = GrantEffect.ALLOW,
        validFrom: Long? = null,
        validUntil: Long? = null,
        maxUses: Long? = null
    ): CapabilityGrant

    suspend fun activateGrant(grantId: GrantId): CapabilityGrant

    suspend fun disableGrant(grantId: GrantId): CapabilityGrant

    suspend fun revokeGrant(grantId: GrantId, reason: RevocationReason): CapabilityGrant

    suspend fun getGrant(grantId: GrantId): CapabilityGrant?

    suspend fun listPeerGrants(peerNodeId: NodeId): List<CapabilityGrant>

    suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision

    suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?>

    suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext = AuthorizationContext()): AuthorizationLease

    suspend fun consumeUsage(leaseId: AuthorizationLeaseId, usage: Long)

    suspend fun completeOperation(leaseId: AuthorizationLeaseId, result: String)

    suspend fun releaseReservation(reservationId: ReservationId)

    suspend fun getPeerUsage(peerNodeId: NodeId): List<UsageCounter>

    suspend fun getPeerRateLimits(peerNodeId: NodeId): List<RateLimitBucket>

    suspend fun getPeerQuotas(peerNodeId: NodeId): List<UsageCounter>

    suspend fun explainDecision(decisionId: DecisionId): AuthorizationExplanation

    suspend fun recover()

    fun subscribeSecurityEvents(listener: SecurityEventListener)
}
