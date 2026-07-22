package spike.dtnrouting.domain

import spike.identity.domain.NodeId

sealed class DtnRoutingError(
    val errorCode: DtnErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class EnvelopeMalformed(val envelopeId: EnvelopeId, detail: String) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_MALFORMED, "Envelope ${envelopeId.value} malformed: $detail")

    class EnvelopeVersionUnsupported(val version: String) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_VERSION_UNSUPPORTED, "Unsupported DTN protocol version: $version")

    class EnvelopeAuthenticationFailed(val envelopeId: EnvelopeId) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_AUTHENTICATION_FAILED, "Authentication failed for envelope: ${envelopeId.value}")

    class EnvelopeDestinationMismatch(val expected: NodeId, val actual: NodeId) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_DESTINATION_MISMATCH, "Envelope destination mismatch: expected ${expected.value}, actual ${actual.value}")

    class EnvelopeHashMismatch(val expected: String, val actual: String) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_HASH_MISMATCH, "Payload ciphertext hash mismatch: expected $expected, actual $actual")

    class EnvelopeExpired(val envelopeId: EnvelopeId, val expiredAt: Long) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_EXPIRED, "Envelope ${envelopeId.value} expired at $expiredAt")

    class EnvelopeHopLimitExceeded(val envelopeId: EnvelopeId, val limit: Int) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_HOP_LIMIT_EXCEEDED, "Hop limit exceeded ($limit) for envelope ${envelopeId.value}")

    class EnvelopeCopyBudgetInvalid(val envelopeId: EnvelopeId, val budget: Int) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_COPY_BUDGET_INVALID, "Invalid copy budget ($budget) for envelope ${envelopeId.value}")

    class EnvelopeAlreadyKnown(val envelopeId: EnvelopeId) :
        DtnRoutingError(DtnErrorCode.ENVELOPE_ALREADY_KNOWN, "Envelope ${envelopeId.value} is already known/stored")

    class RoutingSummaryMalformed(val detail: String) :
        DtnRoutingError(DtnErrorCode.ROUTING_SUMMARY_MALFORMED, "Routing summary malformed: $detail")

    class RoutingSummaryVersionRollback(val nodeId: NodeId, val currentVersion: Long, val receivedVersion: Long) :
        DtnRoutingError(DtnErrorCode.ROUTING_SUMMARY_VERSION_ROLLBACK, "Routing summary rollback for node ${nodeId.value}: current $currentVersion, received $receivedVersion")

    class NoEligibleRoute(val destinationNodeId: NodeId) :
        DtnRoutingError(DtnErrorCode.NO_ELIGIBLE_ROUTE, "No eligible route found for destination: ${destinationNodeId.value}")

    class ForwardingLoopPrevented(val envelopeId: EnvelopeId, val targetPeer: NodeId) :
        DtnRoutingError(DtnErrorCode.FORWARDING_LOOP_PREVENTED, "Forwarding loop prevented for envelope ${envelopeId.value} to peer ${targetPeer.value}")

    class ForwardingQuotaExceeded(val peerNodeId: NodeId, val detail: String) :
        DtnRoutingError(DtnErrorCode.FORWARDING_QUOTA_EXCEEDED, "Forwarding quota exceeded for peer ${peerNodeId.value}: $detail")

    class RelayUnauthorized(val peerNodeId: NodeId, val reason: String) :
        DtnRoutingError(DtnErrorCode.RELAY_UNAUTHORIZED, "Relay unauthorized for peer ${peerNodeId.value}: $reason")

    class CustodyUnauthorized(val peerNodeId: NodeId, val reason: String) :
        DtnRoutingError(DtnErrorCode.CUSTODY_UNAUTHORIZED, "Custody unauthorized for peer ${peerNodeId.value}: $reason")

    class CustodyRefused(val envelopeId: EnvelopeId, val reason: String) :
        DtnRoutingError(DtnErrorCode.CUSTODY_REFUSED, "Custody refused for envelope ${envelopeId.value}: $reason")

    class ReceiptAuthenticationFailed(val receiptId: ReceiptId) :
        DtnRoutingError(DtnErrorCode.RECEIPT_AUTHENTICATION_FAILED, "Receipt authentication failed: ${receiptId.value}")

    class DuplicateDeliverySuppressed(val envelopeId: EnvelopeId) :
        DtnRoutingError(DtnErrorCode.DUPLICATE_DELIVERY_SUPPRESSED, "Duplicate delivery suppressed by tombstone for envelope ${envelopeId.value}")

    class StorageQuotaExceeded(val requiredBytes: Long, val availableBytes: Long) :
        DtnRoutingError(DtnErrorCode.STORAGE_QUOTA_EXCEEDED, "Storage quota exceeded: required $requiredBytes, available $availableBytes")

    class BackpressureCritical(val level: BackpressureLevel) :
        DtnRoutingError(DtnErrorCode.BACKPRESSURE_CRITICAL, "Operation rejected under critical backpressure: $level")

    class RecoveryFailure(val detail: String, cause: Throwable? = null) :
        DtnRoutingError(DtnErrorCode.RECOVERY_FAILURE, "Recovery failure: $detail", cause)

    class InternalError(val detail: String, cause: Throwable? = null) :
        DtnRoutingError(DtnErrorCode.INTERNAL_ERROR, detail, cause)
}
