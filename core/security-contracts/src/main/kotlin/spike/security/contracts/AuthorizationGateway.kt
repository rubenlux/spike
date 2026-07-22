package spike.security.contracts

import spike.identity.domain.NodeId
import spike.security.domain.AuthorizationContext
import spike.security.domain.AuthorizationDecision
import spike.security.domain.AuthorizationLease
import spike.security.domain.AuthorizationLeaseId
import spike.security.domain.AuthorizationRequest
import spike.security.domain.ReservationId
import spike.security.domain.ResourceReservation

/**
 * Normative Authorization Gateway Contract per SPEC-009 Sections 41 & 90.
 * Mandatory entry point for all protected operations (messaging, object transfer, etc.).
 * Must contain interface declarations only with zero business logic.
 */
interface AuthorizationGateway {
    suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision

    suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?>

    suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext = AuthorizationContext()): AuthorizationLease

    suspend fun consumeUsage(leaseId: AuthorizationLeaseId, actualUsage: Long)

    suspend fun completeOperation(leaseId: AuthorizationLeaseId, operationResult: String)

    suspend fun releaseReservation(reservationId: ReservationId)

    suspend fun isPeerBlocked(peerNodeId: NodeId): Boolean
}
