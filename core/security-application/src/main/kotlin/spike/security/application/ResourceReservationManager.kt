package spike.security.application

import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.storage.SecurityRepository

/**
 * Resource Reservation Manager per SPEC-009 Sections 43-46.
 */
class ResourceReservationManager(
    private val repository: SecurityRepository,
    private val quotaManager: QuotaManager
) {
    suspend fun createReservation(
        peerNodeId: NodeId,
        capabilityId: CapabilityId,
        resourceType: ResourceType,
        resourceId: String?,
        requestedUsage: Long,
        ttlMs: Long,
        nowEpochMs: Long
    ): ResourceReservation {
        val reservation = ResourceReservation(
            reservationId = ReservationId.generate(),
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            resourceType = resourceType,
            resourceId = resourceId,
            reservedUsage = requestedUsage,
            consumedUsage = 0L,
            state = ReservationState.ACTIVE,
            createdAt = nowEpochMs,
            expiresAt = nowEpochMs + ttlMs
        )
        repository.saveReservation(reservation)
        return reservation
    }

    suspend fun releaseReservation(reservationId: ReservationId, nowEpochMs: Long) {
        val reservation = repository.getReservation(reservationId) ?: return
        if (reservation.state != ReservationState.ACTIVE && reservation.state != ReservationState.PARTIALLY_CONSUMED) return

        val unconsumed = reservation.unconsumedReserved
        if (unconsumed > 0) {
            quotaManager.releaseReservedQuota(
                peerNodeId = reservation.peerNodeId,
                capabilityId = reservation.capabilityId,
                dimension = UsageDimension.DURABLE_OBJECT_BYTES,
                reservedUsage = unconsumed,
                nowEpochMs = nowEpochMs
            )
        }

        repository.saveReservation(
            reservation.copy(
                state = ReservationState.RELEASED,
                releasedAt = nowEpochMs
            )
        )
    }

    suspend fun consumeReservationUsage(
        reservationId: ReservationId,
        actualConsumedUsage: Long,
        nowEpochMs: Long
    ) {
        val reservation = repository.getReservation(reservationId)
            ?: throw LocalPeerSecurityError.ReservationError.ReservationNotFound(reservationId)

        if (reservation.state == ReservationState.EXPIRED || reservation.state == ReservationState.RELEASED) {
            throw LocalPeerSecurityError.ReservationError.ReservationStateConflict("Cannot consume inactive reservation: ${reservation.state}")
        }

        val newConsumed = reservation.consumedUsage + actualConsumedUsage
        if (newConsumed > reservation.reservedUsage) {
            throw LocalPeerSecurityError.ReservationError.ReservationUsageExceeded(
                reserved = reservation.reservedUsage,
                attempted = newConsumed
            )
        }

        val newState = if (newConsumed == reservation.reservedUsage) ReservationState.CONSUMED else ReservationState.PARTIALLY_CONSUMED

        repository.saveReservation(
            reservation.copy(
                consumedUsage = newConsumed,
                state = newState,
                completedAt = if (newState == ReservationState.CONSUMED) nowEpochMs else null
            )
        )

        quotaManager.commitQuotaUsage(
            peerNodeId = reservation.peerNodeId,
            capabilityId = reservation.capabilityId,
            dimension = UsageDimension.DURABLE_OBJECT_BYTES,
            reservedUsage = actualConsumedUsage,
            actualConsumed = actualConsumedUsage,
            nowEpochMs = nowEpochMs
        )
    }
}
