package spike.servicediscovery.application

import spike.identity.domain.NodeId
import spike.security.contracts.LocalPeerSecurityCore
import spike.security.contracts.SecurityEventListener
import spike.security.domain.*
import java.util.concurrent.ConcurrentHashMap

class FakeLocalPeerSecurityCore : LocalPeerSecurityCore {
    val allowedCapabilities = ConcurrentHashMap.newKeySet<String>()

    override suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision {
        val capRaw = request.capabilityId.raw
        val isAllowed = allowedCapabilities.contains(capRaw)

        return AuthorizationDecision(
            requestId = request.requestId,
            peerNodeId = request.peerNodeId,
            capabilityId = request.capabilityId,
            effect = if (isAllowed) AuthorizationEffect.ALLOW else AuthorizationEffect.DENY,
            reasonCode = if (isAllowed) AuthorizationReason.ALLOWED_BY_GRANT else AuthorizationReason.DENIED_NO_MATCHING_GRANT,
            decidedAt = System.currentTimeMillis()
        )
    }

    private fun fakePeerProfile(peerNodeId: NodeId): PeerSecurityProfile {
        val now = System.currentTimeMillis()
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = TrustState.OBSERVED,
            firstSeenAt = now,
            lastSeenAt = now,
            createdAt = now,
            updatedAt = now
        )
    }

    override suspend fun observePeer(peerNodeId: NodeId): PeerSecurityProfile = fakePeerProfile(peerNodeId)
    override suspend fun getPeerSecurityProfile(peerNodeId: NodeId): PeerSecurityProfile? = null
    override suspend fun setTrustState(peerNodeId: NodeId, trustState: TrustState, reason: String): PeerSecurityProfile = fakePeerProfile(peerNodeId)
    override suspend fun trustPeer(peerNodeId: NodeId): PeerSecurityProfile {
        val now = System.currentTimeMillis()
        return PeerSecurityProfile(peerNodeId = peerNodeId, trustState = TrustState.TRUSTED, firstSeenAt = now, lastSeenAt = now, createdAt = now, updatedAt = now)
    }
    override suspend fun restrictPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile = fakePeerProfile(peerNodeId)
    override suspend fun removeTrust(peerNodeId: NodeId, reason: String): PeerSecurityProfile = fakePeerProfile(peerNodeId)
    override suspend fun blockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile = fakePeerProfile(peerNodeId)
    override suspend fun unblockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile = fakePeerProfile(peerNodeId)
    override suspend fun registerCapability(definition: CapabilityDefinition) {}
    override suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition? = null
    override suspend fun listCapabilities(): List<CapabilityDefinition> = emptyList()
    override suspend fun createGrant(peerNodeId: NodeId, capabilityId: CapabilityId, constraints: ConstraintSet, effect: GrantEffect, validFrom: Long?, validUntil: Long?, maxUses: Long?): CapabilityGrant {
        val now = System.currentTimeMillis()
        return CapabilityGrant(peerNodeId = peerNodeId, capabilityId = capabilityId, effect = effect, constraints = constraints, maxUses = maxUses, createdAt = now, updatedAt = now)
    }
    override suspend fun activateGrant(grantId: GrantId): CapabilityGrant {
        val now = System.currentTimeMillis()
        return CapabilityGrant(peerNodeId = NodeId("dummy"), capabilityId = CapabilityId("dummy.resource.action"), effect = GrantEffect.ALLOW, constraints = ConstraintSet(), createdAt = now, updatedAt = now)
    }
    override suspend fun disableGrant(grantId: GrantId): CapabilityGrant = activateGrant(grantId)
    override suspend fun revokeGrant(grantId: GrantId, reason: RevocationReason): CapabilityGrant = activateGrant(grantId)
    override suspend fun getGrant(grantId: GrantId): CapabilityGrant? = null
    override suspend fun listPeerGrants(peerNodeId: NodeId): List<CapabilityGrant> = emptyList()
    override suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?> = Pair(authorize(request), null)
    override suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext): AuthorizationLease {
        val now = System.currentTimeMillis()
        return AuthorizationLease(
            decisionId = DecisionId.generate(),
            peerNodeId = NodeId("dummy"),
            capabilityId = CapabilityId("dummy.resource.action"),
            policyVersion = PolicyVersion(1L),
            trustVersion = TrustVersion(1L),
            revocationVersion = RevocationVersion(1L),
            issuedAt = now,
            expiresAt = now + 3_600_000L,
            lastValidatedAt = now
        )
    }
    override suspend fun consumeUsage(leaseId: AuthorizationLeaseId, usage: Long) {}
    override suspend fun completeOperation(leaseId: AuthorizationLeaseId, result: String) {}
    override suspend fun releaseReservation(reservationId: ReservationId) {}
    override suspend fun getPeerUsage(peerNodeId: NodeId): List<UsageCounter> = emptyList()
    override suspend fun getPeerRateLimits(peerNodeId: NodeId): List<RateLimitBucket> = emptyList()
    override suspend fun getPeerQuotas(peerNodeId: NodeId): List<UsageCounter> = emptyList()
    override suspend fun explainDecision(decisionId: DecisionId): AuthorizationExplanation {
        return AuthorizationExplanation(
            decisionId = decisionId,
            effect = AuthorizationEffect.ALLOW,
            reasonCode = AuthorizationReason.ALLOWED_BY_GRANT,
            peerTrustState = TrustState.TRUSTED,
            capabilityId = CapabilityId("dummy.resource.action"),
            matchedAllowGrants = emptyList(),
            matchedDenyGrants = emptyList(),
            failedConstraints = emptyList(),
            policyVersion = PolicyVersion(1L),
            decidedAt = System.currentTimeMillis()
        )
    }
    override suspend fun recover() {}
    override fun subscribeSecurityEvents(listener: SecurityEventListener) {}
}
