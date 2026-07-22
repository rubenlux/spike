package spike.peerdirectory.application

import spike.identity.domain.NodeId
import spike.security.contracts.LocalPeerSecurityCore
import spike.security.contracts.SecurityEventListener
import spike.security.domain.*
import java.util.concurrent.ConcurrentHashMap

class FakeLocalPeerSecurityCore : LocalPeerSecurityCore {
    val peerTrustStates = ConcurrentHashMap<NodeId, TrustState>()
    val grants = ConcurrentHashMap<GrantId, CapabilityGrant>()

    override suspend fun observePeer(peerNodeId: NodeId): PeerSecurityProfile {
        val state = peerTrustStates.getOrPut(peerNodeId) { TrustState.OBSERVED }
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = state,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }

    override suspend fun getPeerSecurityProfile(peerNodeId: NodeId): PeerSecurityProfile? {
        return peerTrustStates[peerNodeId]?.let {
            PeerSecurityProfile(peerNodeId = peerNodeId, trustState = it, firstSeenAt = 100L, lastSeenAt = 100L, createdAt = 100L, updatedAt = 100L)
        }
    }

    override suspend fun setTrustState(peerNodeId: NodeId, trustState: TrustState, reason: String): PeerSecurityProfile {
        peerTrustStates[peerNodeId] = trustState
        return observePeer(peerNodeId)
    }

    override suspend fun trustPeer(peerNodeId: NodeId): PeerSecurityProfile {
        peerTrustStates[peerNodeId] = TrustState.TRUSTED
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = TrustState.TRUSTED,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }

    override suspend fun restrictPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        peerTrustStates[peerNodeId] = TrustState.RESTRICTED
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = TrustState.RESTRICTED,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }

    override suspend fun removeTrust(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        peerTrustStates[peerNodeId] = TrustState.OBSERVED
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = TrustState.OBSERVED,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }

    override suspend fun blockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        peerTrustStates[peerNodeId] = TrustState.BLOCKED
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = TrustState.BLOCKED,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }

    override suspend fun unblockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        peerTrustStates[peerNodeId] = TrustState.OBSERVED
        return observePeer(peerNodeId)
    }

    override suspend fun registerCapability(definition: CapabilityDefinition) {}

    override suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition? = null

    override suspend fun listCapabilities(): List<CapabilityDefinition> = emptyList()

    override suspend fun createGrant(
        peerNodeId: NodeId,
        capabilityId: CapabilityId,
        constraints: ConstraintSet,
        effect: GrantEffect,
        validFrom: Long?,
        validUntil: Long?,
        maxUses: Long?
    ): CapabilityGrant {
        val grant = CapabilityGrant(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = effect,
            constraints = constraints,
            maxUses = maxUses,
            createdAt = 100L,
            updatedAt = 100L
        )
        grants[grant.grantId] = grant
        return grant
    }

    override suspend fun activateGrant(grantId: GrantId): CapabilityGrant {
        val grant = grants[grantId]!!
        val updated = grant.copy(state = GrantState.ACTIVE)
        grants[grantId] = updated
        return updated
    }

    override suspend fun disableGrant(grantId: GrantId): CapabilityGrant {
        val grant = grants[grantId]!!
        val updated = grant.copy(state = GrantState.DISABLED)
        grants[grantId] = updated
        return updated
    }

    override suspend fun revokeGrant(grantId: GrantId, reason: RevocationReason): CapabilityGrant {
        val grant = grants[grantId]!!
        val revoked = grant.copy(state = GrantState.REVOKED)
        grants[grantId] = revoked
        return revoked
    }

    override suspend fun getGrant(grantId: GrantId): CapabilityGrant? = grants[grantId]

    override suspend fun listPeerGrants(peerNodeId: NodeId): List<CapabilityGrant> {
        return grants.values.filter { it.peerNodeId == peerNodeId }
    }

    override suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision {
        return AuthorizationDecision(
            requestId = request.requestId,
            peerNodeId = request.peerNodeId,
            capabilityId = request.capabilityId,
            effect = AuthorizationEffect.ALLOW,
            reasonCode = AuthorizationReason.ALLOWED_BY_GRANT,
            decidedAt = 1000L
        )
    }

    override suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?> {
        return Pair(authorize(request), null)
    }

    override suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext): AuthorizationLease {
        return AuthorizationLease(
            decisionId = DecisionId.generate(),
            peerNodeId = NodeId("node:v1:sha256:0000000000000000000000000000000000000000000000000000000000000000"),
            capabilityId = CapabilityId("test.test.test"),
            policyVersion = PolicyVersion(1L),
            trustVersion = TrustVersion(1L),
            revocationVersion = RevocationVersion(1L),
            issuedAt = 100L,
            expiresAt = 200L,
            lastValidatedAt = 100L
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
            capabilityId = CapabilityId("messaging.message.receive"),
            matchedAllowGrants = emptyList(),
            matchedDenyGrants = emptyList(),
            failedConstraints = emptyList(),
            policyVersion = PolicyVersion(1L),
            decidedAt = 1000L
        )
    }

    override suspend fun recover() {}

    override fun subscribeSecurityEvents(listener: SecurityEventListener) {}
}
