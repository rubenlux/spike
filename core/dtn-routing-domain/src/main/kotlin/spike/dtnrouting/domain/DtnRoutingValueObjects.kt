package spike.dtnrouting.domain

import java.util.UUID

@JvmInline
value class EnvelopeId(val value: String) {
    init {
        require(value.isNotBlank()) { "EnvelopeId cannot be blank" }
    }

    companion object {
        fun generate(): EnvelopeId = EnvelopeId("env_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class PayloadId(val value: String) {
    init {
        require(value.isNotBlank()) { "PayloadId cannot be blank" }
    }

    companion object {
        fun generate(): PayloadId = PayloadId("payload_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class RoutingStrategyId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoutingStrategyId cannot be blank" }
    }
}

@JvmInline
value class HopCount(val value: Int) {
    init {
        require(value >= 0) { "HopCount cannot be negative" }
    }

    /**
     * Invariant: HopCount is strictly monotonic and non-decreasing.
     */
    fun next(): HopCount = HopCount(value + 1)

    fun ensureMonotonic(previous: HopCount): HopCount {
        return HopCount(maxOf(this.value, previous.value))
    }
}

@JvmInline
value class HopLimit(val value: Int) {
    init {
        require(value > 0) { "HopLimit must be positive" }
    }
}

@JvmInline
value class CopyBudget(val value: Int) {
    init {
        require(value >= 0) { "CopyBudget cannot be negative" }
    }

    /**
     * Invariant: Total logical copy budget must not increase.
     * Splitting for binary spray-and-wait yields (remaining, transferred).
     */
    fun split(): Pair<CopyBudget, CopyBudget> {
        if (value <= 1) return Pair(CopyBudget(1), CopyBudget(0))
        val transferred = value / 2
        val remaining = value - transferred
        return Pair(CopyBudget(remaining), CopyBudget(transferred))
    }
}

@JvmInline
value class EnvelopeTtl(val durationMillis: Long) {
    init {
        require(durationMillis > 0L) { "EnvelopeTtl must be positive" }
    }
}

@JvmInline
value class PayloadCiphertextHash(val value: String) {
    init {
        require(value.isNotBlank()) { "PayloadCiphertextHash cannot be blank" }
    }
}

@JvmInline
value class ReceiptId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReceiptId cannot be blank" }
    }

    companion object {
        fun generate(): ReceiptId = ReceiptId("rcpt_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class PredictabilityScore(val value: Double) {
    init {
        require(value in 0.0..1.0) { "PredictabilityScore must be in range [0.0, 1.0]" }
    }

    fun age(decayFactor: Double): PredictabilityScore {
        require(decayFactor in 0.0..1.0) { "Decay factor must be in range [0.0, 1.0]" }
        return PredictabilityScore((value * decayFactor).coerceIn(0.0, 1.0))
    }
}

enum class RoutingStrategyType {
    DIRECT_ONLY,
    CONTROLLED_EPIDEMIC,
    SPRAY_AND_WAIT,
    PREDICTABILITY_BASED
}

enum class EnvelopePriority {
    BULK,
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

enum class EnvelopeStateEnum {
    CREATED,
    STORED,
    FORWARDABLE,
    FORWARDING,
    CUSTODY_PENDING,
    CUSTODY_TRANSFERRED,
    DELIVERED,
    RECEIPT_PENDING,
    EXPIRED,
    EVICTED,
    REJECTED,
    CORRUPTED,
    DELETED
}

enum class CustodyStateEnum {
    REQUESTED,
    VALIDATING,
    ACCEPTED,
    ACTIVE,
    TRANSFER_PENDING,
    TRANSFERRED,
    RELEASED,
    EXPIRED,
    REFUSED,
    FAILED
}

enum class DeliveryStatusEnum {
    SUCCESS,
    DUPLICATE_SUPPRESSED,
    EXPIRED,
    REJECTED,
    FAILED
}

enum class BackpressureLevel {
    NORMAL,
    ELEVATED,
    HIGH,
    CRITICAL
}

enum class DtnErrorCode {
    ENVELOPE_MALFORMED,
    ENVELOPE_VERSION_UNSUPPORTED,
    ENVELOPE_AUTHENTICATION_FAILED,
    ENVELOPE_DESTINATION_MISMATCH,
    ENVELOPE_HASH_MISMATCH,
    ENVELOPE_EXPIRED,
    ENVELOPE_HOP_LIMIT_EXCEEDED,
    ENVELOPE_COPY_BUDGET_INVALID,
    ENVELOPE_ALREADY_KNOWN,
    ROUTING_SUMMARY_MALFORMED,
    ROUTING_SUMMARY_VERSION_ROLLBACK,
    ROUTING_SUMMARY_TOO_LARGE,
    NO_ELIGIBLE_ROUTE,
    FORWARDING_LOOP_PREVENTED,
    FORWARDING_QUOTA_EXCEEDED,
    FORWARDING_RATE_LIMITED,
    RELAY_UNAUTHORIZED,
    CUSTODY_UNAUTHORIZED,
    CUSTODY_REQUEST_INVALID,
    CUSTODY_STORAGE_UNAVAILABLE,
    CUSTODY_REFUSED,
    RECEIPT_AUTHENTICATION_FAILED,
    DUPLICATE_DELIVERY_SUPPRESSED,
    STORAGE_QUOTA_EXCEEDED,
    BACKPRESSURE_CRITICAL,
    RECOVERY_FAILURE,
    INTERNAL_ERROR
}
