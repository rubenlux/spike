package spike.security.application

import spike.identity.domain.NodeId
import spike.security.contracts.AuthorizationGateway
import spike.security.contracts.LocalPeerSecurityCore
import spike.security.contracts.SecurityEventListener
import spike.security.domain.*
import spike.security.storage.SecurityRepository
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Normative Implementation of LocalPeerSecurityCore per SPEC-009 Section 89.
 */
class LocalPeerSecurityCoreImpl(
    private val repository: SecurityRepository,
    private val gateway: AuthorizationGateway,
    private val policyEngine: PolicyEvaluationEngine,
    private val recoveryService: SecurityRecoveryService
) : LocalPeerSecurityCore {

    private val eventListeners = CopyOnWriteArrayList<SecurityEventListener>()

    override suspend fun observePeer(peerNodeId: NodeId): PeerSecurityProfile {
        val now = System.currentTimeMillis()
        val existing = repository.getPeerProfile(peerNodeId)
        if (existing != null) {
            val updated = existing.copy(lastSeenAt = now, updatedAt = now)
            repository.savePeerProfile(updated)
            return updated
        }

        val newProfile = PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = TrustState.OBSERVED,
            firstSeenAt = now,
            lastSeenAt = now,
            createdAt = now,
            updatedAt = now
        )
        repository.savePeerProfile(newProfile)
        emitEvent("PEER_SECURITY_PROFILE_CREATED", peerNodeId, null, now)
        return newProfile
    }

    override suspend fun getPeerSecurityProfile(peerNodeId: NodeId): PeerSecurityProfile? {
        return repository.getPeerProfile(peerNodeId)
    }

    override suspend fun setTrustState(peerNodeId: NodeId, trustState: TrustState, reason: String): PeerSecurityProfile {
        val now = System.currentTimeMillis()
        val profile = observePeer(peerNodeId)
        val updated = TrustStateMachine.transition(profile, trustState, now)
        repository.savePeerProfile(updated)

        emitEvent("PEER_TRUST_STATE_CHANGED", peerNodeId, null, now, mapOf("targetState" to trustState.name, "reason" to reason))
        return updated
    }

    override suspend fun trustPeer(peerNodeId: NodeId): PeerSecurityProfile {
        return setTrustState(peerNodeId, TrustState.TRUSTED, "USER_TRUSTED")
    }

    override suspend fun restrictPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        return setTrustState(peerNodeId, TrustState.RESTRICTED, reason)
    }

    override suspend fun removeTrust(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        return setTrustState(peerNodeId, TrustState.OBSERVED, reason)
    }

    override suspend fun blockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        val now = System.currentTimeMillis()
        val profile = observePeer(peerNodeId)
        val updated = TrustStateMachine.transition(profile, TrustState.BLOCKED, now)
        repository.savePeerProfile(updated)

        // Invalidate active leases for blocked peer
        val activeLeases = repository.listActiveLeasesForPeer(peerNodeId)
        for (lease in activeLeases) {
            repository.saveLease(lease.copy(state = LeaseState.REVOKED))
            emitEvent("AUTHORIZATION_LEASE_REVOKED", peerNodeId, lease.capabilityId, now, mapOf("leaseId" to lease.leaseId.value, "reason" to "PEER_BLOCKED"))
        }

        emitEvent("PEER_BLOCKED", peerNodeId, null, now, mapOf("reason" to reason))
        return updated
    }

    override suspend fun unblockPeer(peerNodeId: NodeId, reason: String): PeerSecurityProfile {
        return setTrustState(peerNodeId, TrustState.OBSERVED, reason)
    }

    override suspend fun registerCapability(definition: CapabilityDefinition) {
        val existing = repository.getCapability(definition.capabilityId)
        if (existing != null) {
            throw LocalPeerSecurityError.CapabilityError.CapabilityAlreadyRegistered(definition.capabilityId)
        }
        repository.saveCapability(definition)
        emitEvent("CAPABILITY_REGISTERED", null, definition.capabilityId, System.currentTimeMillis())
    }

    override suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition? {
        return repository.getCapability(capabilityId)
    }

    override suspend fun listCapabilities(): List<CapabilityDefinition> {
        return repository.listCapabilities()
    }

    override suspend fun createGrant(
        peerNodeId: NodeId,
        capabilityId: CapabilityId,
        constraints: ConstraintSet,
        effect: GrantEffect,
        validFrom: Long?,
        validUntil: Long?,
        maxUses: Long?
    ): CapabilityGrant {
        val now = System.currentTimeMillis()
        val grant = CapabilityGrant(
            grantId = GrantId.generate(),
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = effect,
            state = GrantState.ACTIVE,
            constraints = constraints,
            validFrom = validFrom,
            validUntil = validUntil,
            maxUses = maxUses,
            remainingUses = maxUses,
            createdAt = now,
            updatedAt = now
        )
        repository.saveGrant(grant)
        emitEvent("CAPABILITY_GRANT_CREATED", peerNodeId, capabilityId, now, mapOf("grantId" to grant.grantId.value, "effect" to effect.name))
        return grant
    }

    override suspend fun activateGrant(grantId: GrantId): CapabilityGrant {
        val now = System.currentTimeMillis()
        val grant = repository.getGrant(grantId)
            ?: throw LocalPeerSecurityError.GrantError.GrantNotFound(grantId)
        val updated = grant.copy(state = GrantState.ACTIVE, updatedAt = now)
        repository.saveGrant(updated)
        emitEvent("CAPABILITY_GRANT_ACTIVATED", grant.peerNodeId, grant.capabilityId, now, mapOf("grantId" to grantId.value))
        return updated
    }

    override suspend fun disableGrant(grantId: GrantId): CapabilityGrant {
        val now = System.currentTimeMillis()
        val grant = repository.getGrant(grantId)
            ?: throw LocalPeerSecurityError.GrantError.GrantNotFound(grantId)
        val updated = grant.copy(state = GrantState.DISABLED, updatedAt = now)
        repository.saveGrant(updated)
        return updated
    }

    override suspend fun revokeGrant(grantId: GrantId, reason: RevocationReason): CapabilityGrant {
        val now = System.currentTimeMillis()
        val grant = repository.getGrant(grantId)
            ?: throw LocalPeerSecurityError.GrantError.GrantNotFound(grantId)
        val updated = grant.copy(
            state = GrantState.REVOKED,
            revokedAt = now,
            revocationReason = reason,
            updatedAt = now
        )
        repository.saveGrant(updated)

        // Increment peer revocation version
        val profile = repository.getPeerProfile(grant.peerNodeId)
        if (profile != null) {
            repository.savePeerProfile(profile.copy(revocationVersion = profile.revocationVersion.next(), updatedAt = now))
        }

        emitEvent("CAPABILITY_GRANT_REVOKED", grant.peerNodeId, grant.capabilityId, now, mapOf("grantId" to grantId.value, "reason" to reason.name))
        return updated
    }

    override suspend fun getGrant(grantId: GrantId): CapabilityGrant? {
        return repository.getGrant(grantId)
    }

    override suspend fun listPeerGrants(peerNodeId: NodeId): List<CapabilityGrant> {
        return repository.listCapabilities().flatMap { cap ->
            repository.findMatchingGrants(peerNodeId, cap.capabilityId)
        }
    }

    override suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision {
        val decision = gateway.authorize(request)
        val eventType = if (decision.effect == AuthorizationEffect.ALLOW) "AUTHORIZATION_ALLOWED" else "AUTHORIZATION_DENIED"
        emitEvent(eventType, request.peerNodeId, request.capabilityId, decision.decidedAt, mapOf("reasonCode" to decision.reasonCode.name))
        return decision
    }

    override suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?> {
        val result = gateway.authorizeAndReserve(request)
        val (decision, reservation) = result
        val eventType = if (decision.effect == AuthorizationEffect.ALLOW) "AUTHORIZATION_ALLOWED" else "AUTHORIZATION_DENIED"
        emitEvent(eventType, request.peerNodeId, request.capabilityId, decision.decidedAt, mapOf("reasonCode" to decision.reasonCode.name))
        if (reservation != null) {
            emitEvent("RESOURCE_RESERVATION_CREATED", request.peerNodeId, request.capabilityId, decision.decidedAt, mapOf("reservationId" to reservation.reservationId.value, "reserved" to reservation.reservedUsage.toString()))
        }
        return result
    }

    override suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext): AuthorizationLease {
        return gateway.validateLease(leaseId, context)
    }

    override suspend fun consumeUsage(leaseId: AuthorizationLeaseId, usage: Long) {
        gateway.consumeUsage(leaseId, usage)
    }

    override suspend fun completeOperation(leaseId: AuthorizationLeaseId, result: String) {
        gateway.completeOperation(leaseId, result)
    }

    override suspend fun releaseReservation(reservationId: ReservationId) {
        gateway.releaseReservation(reservationId)
        emitEvent("RESOURCE_RESERVATION_RELEASED", null, null, System.currentTimeMillis(), mapOf("reservationId" to reservationId.value))
    }

    override suspend fun getPeerUsage(peerNodeId: NodeId): List<UsageCounter> {
        return repository.listUsageCountersForPeer(peerNodeId)
    }

    override suspend fun getPeerRateLimits(peerNodeId: NodeId): List<RateLimitBucket> {
        return repository.listRateLimitBucketsForPeer(peerNodeId)
    }

    override suspend fun getPeerQuotas(peerNodeId: NodeId): List<UsageCounter> {
        return repository.listUsageCountersForPeer(peerNodeId)
    }

    override suspend fun explainDecision(decisionId: DecisionId): AuthorizationExplanation {
        val decision = repository.getDecision(decisionId)
            ?: throw LocalPeerSecurityError.AuthorizationError.InvalidAuthorizationRequest("Decision not found: ${decisionId.value}")

        val profile = repository.getPeerProfile(decision.peerNodeId)
        val trustState = profile?.trustState ?: TrustState.UNKNOWN

        return AuthorizationExplanation(
            decisionId = decision.decisionId,
            effect = decision.effect,
            reasonCode = decision.reasonCode,
            peerTrustState = trustState,
            capabilityId = decision.capabilityId,
            matchedAllowGrants = decision.matchedGrantIds,
            matchedDenyGrants = decision.matchedDenyGrantIds,
            failedConstraints = decision.evaluatedConstraints,
            policyVersion = decision.policyVersion,
            decidedAt = decision.decidedAt
        )
    }

    override suspend fun recover() {
        recoveryService.recover()
    }

    override fun subscribeSecurityEvents(listener: SecurityEventListener) {
        eventListeners.add(listener)
    }

    private suspend fun emitEvent(
        eventType: String,
        peerNodeId: NodeId?,
        capabilityId: CapabilityId?,
        timestamp: Long,
        summary: Map<String, String> = emptyMap()
    ) {
        val event = SecurityAuditEvent(
            eventType = eventType,
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            usageSummary = summary,
            timestamp = timestamp
        )
        repository.appendAuditEvent(event)
        eventListeners.forEach { listener ->
            runCatching { listener.onSecurityEvent(event) }
        }
    }
}
