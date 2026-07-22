package spike.security.storage

import spike.identity.domain.NodeId
import spike.security.domain.AuthorizationDecision
import spike.security.domain.AuthorizationLease
import spike.security.domain.AuthorizationLeaseId
import spike.security.domain.CapabilityDefinition
import spike.security.domain.CapabilityGrant
import spike.security.domain.CapabilityId
import spike.security.domain.DecisionId
import spike.security.domain.GrantId
import spike.security.domain.PeerSecurityProfile
import spike.security.domain.RateLimitBucket
import spike.security.domain.ReservationId
import spike.security.domain.ResourceReservation
import spike.security.domain.SecurityAuditEvent
import spike.security.domain.UsageCounter
import spike.security.domain.UsageDimension

/**
 * Pure persistence contract for durable security state per SPEC-009 Section 91.
 * Must remain free of any storage technology details (Room, SQLite, filesystem, etc.).
 */
interface SecurityRepository {
    suspend fun savePeerProfile(profile: PeerSecurityProfile)
    suspend fun getPeerProfile(peerNodeId: NodeId): PeerSecurityProfile?

    suspend fun saveCapability(definition: CapabilityDefinition)
    suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition?
    suspend fun listCapabilities(): List<CapabilityDefinition>

    suspend fun saveGrant(grant: CapabilityGrant)
    suspend fun getGrant(grantId: GrantId): CapabilityGrant?
    suspend fun findMatchingGrants(peerNodeId: NodeId, capabilityId: CapabilityId): List<CapabilityGrant>
    suspend fun incrementGrantUsageAtomically(grantId: GrantId): Boolean

    suspend fun saveDecision(decision: AuthorizationDecision)
    suspend fun getDecision(decisionId: DecisionId): AuthorizationDecision?

    suspend fun saveLease(lease: AuthorizationLease)
    suspend fun getLease(leaseId: AuthorizationLeaseId): AuthorizationLease?
    suspend fun listActiveLeasesForPeer(peerNodeId: NodeId): List<AuthorizationLease>

    suspend fun saveReservation(reservation: ResourceReservation)
    suspend fun getReservation(reservationId: ReservationId): ResourceReservation?

    suspend fun saveUsageCounter(counter: UsageCounter)
    suspend fun getUsageCounter(peerNodeId: NodeId, capabilityId: CapabilityId?, dimension: UsageDimension): UsageCounter?
    suspend fun listUsageCountersForPeer(peerNodeId: NodeId): List<UsageCounter>

    suspend fun saveRateLimitBucket(bucket: RateLimitBucket)
    suspend fun getRateLimitBucket(peerNodeId: NodeId, capabilityId: CapabilityId, dimension: UsageDimension): RateLimitBucket?
    suspend fun listRateLimitBucketsForPeer(peerNodeId: NodeId): List<RateLimitBucket>

    suspend fun appendAuditEvent(event: SecurityAuditEvent)
    suspend fun listAuditEvents(peerNodeId: NodeId? = null, limit: Int = 100): List<SecurityAuditEvent>

    suspend fun <T> transaction(block: suspend () -> T): T
}
