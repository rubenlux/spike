package spike.security.application

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.storage.SecurityRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class InMemorySecurityRepository : SecurityRepository {
    val profiles = ConcurrentHashMap<NodeId, PeerSecurityProfile>()
    val capabilities = ConcurrentHashMap<CapabilityId, CapabilityDefinition>()
    val grants = ConcurrentHashMap<GrantId, CapabilityGrant>()
    val decisions = ConcurrentHashMap<DecisionId, AuthorizationDecision>()
    val leases = ConcurrentHashMap<AuthorizationLeaseId, AuthorizationLease>()
    val reservations = ConcurrentHashMap<ReservationId, ResourceReservation>()
    val usageCounters = ConcurrentHashMap<String, UsageCounter>()
    val rateLimitBuckets = ConcurrentHashMap<String, RateLimitBucket>()
    val auditEvents = CopyOnWriteArrayList<SecurityAuditEvent>()

    private val grantLock = Mutex()

    override suspend fun savePeerProfile(profile: PeerSecurityProfile) {
        profiles[profile.peerNodeId] = profile
    }

    override suspend fun getPeerProfile(peerNodeId: NodeId): PeerSecurityProfile? {
        return profiles[peerNodeId]
    }

    override suspend fun saveCapability(definition: CapabilityDefinition) {
        capabilities[definition.capabilityId] = definition
    }

    override suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition? {
        return capabilities[capabilityId]
    }

    override suspend fun listCapabilities(): List<CapabilityDefinition> {
        return capabilities.values.toList()
    }

    override suspend fun saveGrant(grant: CapabilityGrant) {
        grants[grant.grantId] = grant
    }

    override suspend fun getGrant(grantId: GrantId): CapabilityGrant? {
        return grants[grantId]
    }

    override suspend fun findMatchingGrants(peerNodeId: NodeId, capabilityId: CapabilityId): List<CapabilityGrant> {
        return grants.values.filter { it.peerNodeId == peerNodeId && it.capabilityId == capabilityId }
    }

    override suspend fun incrementGrantUsageAtomically(grantId: GrantId): Boolean {
        return grantLock.withLock {
            val grant = grants[grantId] ?: return@withLock false
            val remaining = grant.remainingUses ?: return@withLock true
            if (remaining <= 0) return@withLock false

            val updated = grant.copy(
                remainingUses = remaining - 1,
                state = if (remaining - 1 == 0L) GrantState.EXHAUSTED else grant.state
            )
            grants[grantId] = updated
            true
        }
    }

    override suspend fun saveDecision(decision: AuthorizationDecision) {
        decisions[decision.decisionId] = decision
    }

    override suspend fun getDecision(decisionId: DecisionId): AuthorizationDecision? {
        return decisions[decisionId]
    }

    override suspend fun saveLease(lease: AuthorizationLease) {
        leases[lease.leaseId] = lease
    }

    override suspend fun getLease(leaseId: AuthorizationLeaseId): AuthorizationLease? {
        return leases[leaseId]
    }

    override suspend fun listActiveLeasesForPeer(peerNodeId: NodeId): List<AuthorizationLease> {
        return leases.values.filter { it.peerNodeId == peerNodeId && it.state == LeaseState.ACTIVE }
    }

    override suspend fun saveReservation(reservation: ResourceReservation) {
        reservations[reservation.reservationId] = reservation
    }

    override suspend fun getReservation(reservationId: ReservationId): ResourceReservation? {
        return reservations[reservationId]
    }

    override suspend fun saveUsageCounter(counter: UsageCounter) {
        val key = "${counter.peerNodeId.value}:${counter.capabilityId?.raw ?: "global"}:${counter.usageDimension.name}"
        usageCounters[key] = counter
    }

    override suspend fun getUsageCounter(peerNodeId: NodeId, capabilityId: CapabilityId?, dimension: UsageDimension): UsageCounter? {
        val key = "${peerNodeId.value}:${capabilityId?.raw ?: "global"}:${dimension.name}"
        return usageCounters[key]
    }

    override suspend fun listUsageCountersForPeer(peerNodeId: NodeId): List<UsageCounter> {
        return usageCounters.values.filter { it.peerNodeId == peerNodeId }
    }

    override suspend fun saveRateLimitBucket(bucket: RateLimitBucket) {
        val key = "${bucket.peerNodeId.value}:${bucket.capabilityId.raw}:${bucket.dimension.name}"
        rateLimitBuckets[key] = bucket
    }

    override suspend fun getRateLimitBucket(peerNodeId: NodeId, capabilityId: CapabilityId, dimension: UsageDimension): RateLimitBucket? {
        val key = "${peerNodeId.value}:${capabilityId.raw}:${dimension.name}"
        return rateLimitBuckets[key]
    }

    override suspend fun listRateLimitBucketsForPeer(peerNodeId: NodeId): List<RateLimitBucket> {
        return rateLimitBuckets.values.filter { it.peerNodeId == peerNodeId }
    }

    override suspend fun appendAuditEvent(event: SecurityAuditEvent) {
        auditEvents.add(event)
    }

    override suspend fun listAuditEvents(peerNodeId: NodeId?, limit: Int): List<SecurityAuditEvent> {
        return auditEvents.filter { peerNodeId == null || it.peerNodeId == peerNodeId }.take(limit)
    }

    override suspend fun <T> transaction(block: suspend () -> T): T {
        return block()
    }
}
