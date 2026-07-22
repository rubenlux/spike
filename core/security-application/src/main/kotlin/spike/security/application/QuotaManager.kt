package spike.security.application

import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.storage.SecurityRepository

/**
 * Quota Enforcement Service per SPEC-009 Sections 70-73.
 * Maintains strict invariant: AVAILABLE = LIMIT - CONSUMED - RESERVED.
 */
class QuotaManager(private val repository: SecurityRepository) {

    suspend fun checkAndReserveQuota(
        peerNodeId: NodeId,
        capabilityId: CapabilityId?,
        dimension: UsageDimension,
        requestedUsage: Long,
        limit: Long,
        nowEpochMs: Long
    ): AuthorizationReason? {
        val existingCounter = repository.getUsageCounter(peerNodeId, capabilityId, dimension)
        val counter = existingCounter ?: UsageCounter(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            usageDimension = dimension,
            periodStart = nowEpochMs,
            periodEnd = null,
            consumed = 0L,
            reserved = 0L,
            limit = limit,
            updatedAt = nowEpochMs
        )

        if (counter.available < requestedUsage) {
            return AuthorizationReason.DENIED_STORAGE_QUOTA
        }

        val updatedCounter = counter.copy(
            reserved = counter.reserved + requestedUsage,
            updatedAt = nowEpochMs
        )
        repository.saveUsageCounter(updatedCounter)
        return null
    }

    suspend fun commitQuotaUsage(
        peerNodeId: NodeId,
        capabilityId: CapabilityId?,
        dimension: UsageDimension,
        reservedUsage: Long,
        actualConsumed: Long,
        nowEpochMs: Long
    ) {
        val counter = repository.getUsageCounter(peerNodeId, capabilityId, dimension) ?: return
        val newReserved = (counter.reserved - reservedUsage).coerceAtLeast(0L)
        val newConsumed = counter.consumed + actualConsumed
        
        repository.saveUsageCounter(
            counter.copy(
                consumed = newConsumed,
                reserved = newReserved,
                updatedAt = nowEpochMs
            )
        )
    }

    suspend fun releaseReservedQuota(
        peerNodeId: NodeId,
        capabilityId: CapabilityId?,
        dimension: UsageDimension,
        reservedUsage: Long,
        nowEpochMs: Long
    ) {
        val counter = repository.getUsageCounter(peerNodeId, capabilityId, dimension) ?: return
        val newReserved = (counter.reserved - reservedUsage).coerceAtLeast(0L)
        repository.saveUsageCounter(
            counter.copy(
                reserved = newReserved,
                updatedAt = nowEpochMs
            )
        )
    }
}
