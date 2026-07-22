package spike.security.application

import spike.identity.domain.NodeId
import spike.security.contracts.AuthorizationGateway
import spike.security.domain.*
import spike.security.storage.SecurityRepository

/**
 * Normative Implementation of AuthorizationGateway per SPEC-009 Sections 41 & 90.
 */
class AuthorizationGatewayImpl(
    private val repository: SecurityRepository,
    private val policyEngine: PolicyEvaluationEngine,
    private val reservationManager: ResourceReservationManager
) : AuthorizationGateway {

    override suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision {
        val now = System.currentTimeMillis()
        val decision = policyEngine.evaluate(request, now)
        repository.saveDecision(decision)

        // Decrement grant usage atomically if one-shot or limited-use grant matched
        if (decision.effect == AuthorizationEffect.ALLOW) {
            decision.matchedGrantIds.forEach { grantId ->
                repository.incrementGrantUsageAtomically(grantId)
            }
        }

        return decision
    }

    override suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?> {
        val now = System.currentTimeMillis()
        val decision = authorize(request)

        if (decision.effect == AuthorizationEffect.DENY) {
            return Pair(decision, null)
        }

        val capDef = repository.getCapability(request.capabilityId)
        val reservation = if (capDef?.supportsReservation == true) {
            reservationManager.createReservation(
                peerNodeId = request.peerNodeId,
                capabilityId = request.capabilityId,
                resourceType = capDef.resourceType,
                resourceId = request.resourceId,
                requestedUsage = request.requestedUsage,
                ttlMs = 300_000L, // 5 minutes default reservation TTL
                nowEpochMs = now
            )
        } else {
            null
        }

        return Pair(decision, reservation)
    }

    override suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext): AuthorizationLease {
        val now = System.currentTimeMillis()
        val lease = repository.getLease(leaseId)
            ?: throw LocalPeerSecurityError.LeaseError.AuthorizationLeaseNotFound(leaseId)

        if (lease.state != LeaseState.ACTIVE) {
            throw LocalPeerSecurityError.LeaseError.AuthorizationLeaseRevoked(leaseId)
        }

        if (now > lease.expiresAt) {
            val expiredLease = lease.copy(state = LeaseState.EXPIRED)
            repository.saveLease(expiredLease)
            throw LocalPeerSecurityError.LeaseError.AuthorizationLeaseExpired(leaseId)
        }

        val peerProfile = repository.getPeerProfile(lease.peerNodeId)
        if (peerProfile?.trustState == TrustState.BLOCKED) {
            val revokedLease = lease.copy(state = LeaseState.REVOKED)
            repository.saveLease(revokedLease)
            throw LocalPeerSecurityError.LeaseError.AuthorizationLeaseRevoked(leaseId)
        }

        if (peerProfile != null && peerProfile.revocationVersion.value > lease.revocationVersion.value) {
            val revokedLease = lease.copy(state = LeaseState.REVOKED)
            repository.saveLease(revokedLease)
            throw LocalPeerSecurityError.LeaseError.AuthorizationLeaseRevoked(leaseId)
        }

        val updatedLease = lease.copy(lastValidatedAt = now)
        repository.saveLease(updatedLease)
        return updatedLease
    }

    override suspend fun consumeUsage(leaseId: AuthorizationLeaseId, actualUsage: Long) {
        validateLease(leaseId)
        // Additional lease usage accounting handled during completeOperation or checkpoint
    }

    override suspend fun completeOperation(leaseId: AuthorizationLeaseId, operationResult: String) {
        val lease = repository.getLease(leaseId)
            ?: throw LocalPeerSecurityError.LeaseError.AuthorizationLeaseNotFound(leaseId)

        val newState = if (operationResult.equals("SUCCESS", ignoreCase = true)) LeaseState.COMPLETED else LeaseState.FAILED
        repository.saveLease(lease.copy(state = newState))
    }

    override suspend fun releaseReservation(reservationId: ReservationId) {
        val now = System.currentTimeMillis()
        reservationManager.releaseReservation(reservationId, now)
    }

    override suspend fun isPeerBlocked(peerNodeId: NodeId): Boolean {
        val profile = repository.getPeerProfile(peerNodeId)
        return profile?.trustState == TrustState.BLOCKED
    }
}
