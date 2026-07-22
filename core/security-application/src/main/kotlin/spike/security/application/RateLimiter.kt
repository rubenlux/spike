package spike.security.application

import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.storage.SecurityRepository

/**
 * Fixed Window Rate Limiter v1 per SPEC-009 Sections 66-69.
 */
class RateLimiter(private val repository: SecurityRepository) {

    suspend fun checkAndReserveRateLimit(
        peerNodeId: NodeId,
        capabilityId: CapabilityId,
        constraint: RateLimitConstraint,
        requestedUsage: Long,
        nowEpochMs: Long
    ): AuthorizationReason? {
        val windowMs = constraint.windowSeconds * 1000L
        val currentWindowStart = (nowEpochMs / windowMs) * windowMs
        val currentWindowEnd = currentWindowStart + windowMs

        val existingBucket = repository.getRateLimitBucket(peerNodeId, capabilityId, constraint.dimension)
        
        val bucket = if (existingBucket == null || existingBucket.windowStart < currentWindowStart) {
            RateLimitBucket(
                peerNodeId = peerNodeId,
                capabilityId = capabilityId,
                dimension = constraint.dimension,
                windowStart = currentWindowStart,
                windowEnd = currentWindowEnd,
                limit = constraint.limit,
                consumed = 0L,
                reserved = 0L,
                updatedAt = nowEpochMs
            )
        } else {
            existingBucket
        }

        if (bucket.available < requestedUsage) {
            return AuthorizationReason.DENIED_RATE_LIMIT
        }

        val updatedBucket = bucket.copy(
            reserved = bucket.reserved + requestedUsage,
            updatedAt = nowEpochMs
        )
        repository.saveRateLimitBucket(updatedBucket)
        return null
    }
}
