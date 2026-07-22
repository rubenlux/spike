package spike.security.domain

import java.util.UUID

/**
 * Capability identifier following namespace.resource.action canonical format.
 */
@JvmInline
value class CapabilityId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "CapabilityId cannot be blank" }
        val parts = raw.split(".")
        require(parts.size >= 3) { "CapabilityId must follow 'namespace.resource.action' format: $raw" }
        require(parts.all { it.isNotBlank() }) { "CapabilityId segments cannot be blank: $raw" }
    }

    val namespace: String get() = raw.split(".").first()
    val resource: String get() = raw.split(".").drop(1).dropLast(1).joinToString(".")
    val action: String get() = raw.split(".").last()

    override fun toString(): String = raw
}

/** Strongly typed domain identifiers as zero-allocation inline value classes. */
@JvmInline
value class GrantId(val value: String) {
    companion object {
        fun generate(): GrantId = GrantId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class DecisionId(val value: String) {
    companion object {
        fun generate(): DecisionId = DecisionId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AuthorizationLeaseId(val value: String) {
    companion object {
        fun generate(): AuthorizationLeaseId = AuthorizationLeaseId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class ReservationId(val value: String) {
    companion object {
        fun generate(): ReservationId = ReservationId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class PolicyId(val value: String) {
    companion object {
        fun generate(): PolicyId = PolicyId(UUID.randomUUID().toString())
    }
}

/** Monotonically increasing version wrappers as inline value classes. */
@JvmInline
value class PolicyVersion(val value: Long = 1L) {
    fun next(): PolicyVersion = PolicyVersion(value + 1)
}

@JvmInline
value class TrustVersion(val value: Long = 1L) {
    fun next(): TrustVersion = TrustVersion(value + 1)
}

@JvmInline
value class RevocationVersion(val value: Long = 1L) {
    fun next(): RevocationVersion = RevocationVersion(value + 1)
}

@JvmInline
value class ResourceType(val value: String)

@JvmInline
value class OperationType(val value: String)

/** Trust states for local peer profile classification. */
enum class TrustState {
    UNKNOWN,
    OBSERVED,
    PENDING_APPROVAL,
    TRUSTED,
    RESTRICTED,
    BLOCKED
}

/** Capability Grant Effects. */
enum class GrantEffect {
    ALLOW,
    DENY
}

/** Capability Grant Lifecycle States. */
enum class GrantState {
    PENDING,
    ACTIVE,
    EXHAUSTED,
    EXPIRED,
    REVOKED,
    DISABLED
}

/** Authorization Decision Effects. */
enum class AuthorizationEffect {
    ALLOW,
    DENY
}

/** Reason codes for explainable authorization decisions. */
enum class AuthorizationReason {
    ALLOWED_BY_GRANT,
    DENIED_BY_DEFAULT,
    DENIED_PEER_BLOCKED,
    DENIED_PEER_NOT_TRUSTED,
    DENIED_EXPLICIT_GRANT,
    DENIED_UNKNOWN_CAPABILITY,
    DENIED_NO_MATCHING_GRANT,
    DENIED_GRANT_EXPIRED,
    DENIED_GRANT_EXHAUSTED,
    DENIED_TIME_WINDOW,
    DENIED_MESSAGE_TYPE,
    DENIED_PAYLOAD_SIZE,
    DENIED_OBJECT_SIZE,
    DENIED_MEDIA_TYPE,
    DENIED_STORAGE_QUOTA,
    DENIED_RATE_LIMIT,
    DENIED_CONCURRENCY_LIMIT,
    DENIED_RESOURCE_SCOPE,
    DENIED_APPLICATION_NAMESPACE,
    DENIED_POLICY_CONFLICT,
    DENIED_INVALID_CONTEXT,
    DENIED_REVOCATION_VERSION,
    DENIED_SECURITY_STATE_UNAVAILABLE
}

/** Dimensions for usage accounting, rate limits, and quotas. */
enum class UsageDimension {
    OPERATIONS_PER_SECOND,
    OPERATIONS_PER_MINUTE,
    OPERATIONS_PER_HOUR,
    BYTES_PER_MINUTE,
    BYTES_PER_HOUR,
    MESSAGES_PER_MINUTE,
    TRANSFERS_PER_HOUR,
    STORED_INBOX_BYTES,
    STORED_OUTBOX_BYTES,
    INBOUND_TEMP_TRANSFER_BYTES,
    DURABLE_OBJECT_BYTES,
    ACTIVE_TRANSFERS,
    ACTIVE_MESSAGE_DELIVERIES,
    UNPROCESSED_INBOX_MESSAGES,
    DEAD_LETTER_MESSAGES,
    OPERATIONS_PER_CAPABILITY
}

/** Reasons for grant or trust revocation. */
enum class RevocationReason {
    USER_REVOKED,
    PEER_BLOCKED,
    TRUST_DOWNGRADED,
    EXPIRED,
    EXHAUSTED,
    SECURITY_POLICY_CHANGED,
    INACTIVITY,
    SECURITY_STATE_INCONSISTENCY
}

/** Policy governing how active operations are revoked. */
enum class ActiveOperationRevocationPolicy {
    ALLOW_TO_COMPLETE,
    STOP_AT_NEXT_CHECKPOINT,
    STOP_IMMEDIATELY_IF_SAFE
}

/** States of a resource reservation. */
enum class ReservationState {
    ACTIVE,
    PARTIALLY_CONSUMED,
    CONSUMED,
    RELEASED,
    EXPIRED,
    REVOKED
}

/** States of an active authorization lease. */
enum class LeaseState {
    ACTIVE,
    EXPIRED,
    REVOKED,
    COMPLETED,
    FAILED
}
