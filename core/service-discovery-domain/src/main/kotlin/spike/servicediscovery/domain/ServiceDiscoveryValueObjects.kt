package spike.servicediscovery.domain

import java.util.UUID

@JvmInline
value class ServiceId(val value: String) {
    init {
        require(value.isNotBlank()) { "ServiceId cannot be blank" }
    }
}

@JvmInline
value class ContractId(val value: String) {
    init {
        require(value.isNotBlank()) { "ContractId cannot be blank" }
    }
}

@JvmInline
value class ContractVersion(val value: String) {
    init {
        require(value.matches(Regex("""^\d+\.\d+$"""))) { "ContractVersion must be in MAJOR.MINOR format, e.g. 1.0" }
    }

    val major: Int get() = value.split(".")[0].toInt()
    val minor: Int get() = value.split(".")[1].toInt()

    fun isCompatibleWith(clientRequiredVersion: ContractVersion): Boolean {
        return this.major == clientRequiredVersion.major && this.minor >= clientRequiredVersion.minor
    }
}

@JvmInline
value class MethodId(val value: String) {
    init {
        require(value.isNotBlank()) { "MethodId cannot be blank" }
    }
}

@JvmInline
value class InvocationId(val value: String) {
    init {
        require(value.isNotBlank()) { "InvocationId cannot be blank" }
    }

    companion object {
        fun generate(): InvocationId = InvocationId("inv_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class ExecutionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExecutionId cannot be blank" }
    }

    companion object {
        fun generate(): ExecutionId = ExecutionId("exec_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "IdempotencyKey cannot be blank" }
    }

    companion object {
        fun generate(): IdempotencyKey = IdempotencyKey("idempotency_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class AdvertisementId(val value: String) {
    init {
        require(value.isNotBlank()) { "AdvertisementId cannot be blank" }
    }

    companion object {
        fun generate(): AdvertisementId = AdvertisementId("adv_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class AdvertisementVersion(val value: Long) {
    init {
        require(value >= 0L) { "AdvertisementVersion must be non-negative" }
    }
}

@JvmInline
value class ServiceInstanceId(val value: String) {
    init {
        require(value.isNotBlank()) { "ServiceInstanceId cannot be blank" }
    }

    companion object {
        fun generate(): ServiceInstanceId = ServiceInstanceId("inst_${UUID.randomUUID().toString().replace("-", "")}")
    }
}

@JvmInline
value class ContractHash(val value: String) {
    init {
        require(value.isNotBlank()) { "ContractHash cannot be blank" }
    }
}

@JvmInline
value class Deadline(val epochMillis: Long)

@JvmInline
value class AvailabilityTtl(val durationMillis: Long)

@JvmInline
value class CancellationReason(val value: String)

enum class RpcMethodSemantics {
    QUERY,
    IDEMPOTENT_COMMAND,
    NON_IDEMPOTENT_COMMAND,
    ONE_WAY_COMMAND
}

enum class RpcInvocationState {
    CREATED,
    PERSISTED,
    AUTHORIZED,
    WAITING_REQUEST_OBJECTS,
    READY_TO_SEND,
    SENDING,
    REQUEST_DELIVERED,
    REMOTE_ACCEPTED,
    EXECUTING,
    CANCELLATION_REQUESTED,
    RESULT_AVAILABLE,
    RESULT_RECEIVED,
    COMPLETED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    FAILED,
    UNKNOWN_OUTCOME
}

enum class RpcExecutionState {
    RECEIVED,
    VALIDATED,
    AUTHORIZED,
    WAITING_REQUEST_OBJECTS,
    ACCEPTED,
    RUNNING,
    CANCELLATION_REQUESTED,
    COMMITTING_RESULT,
    RESULT_DURABLE,
    RESPONDING,
    ACKNOWLEDGED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    FAILED,
    UNKNOWN_OUTCOME
}

enum class RpcResultStatus {
    SUCCESS,
    APPLICATION_ERROR,
    PROTOCOL_ERROR,
    AUTHORIZATION_DENIED,
    CANCELLED,
    EXPIRED,
    UNKNOWN_OUTCOME
}

enum class ServiceAvailabilityState {
    AVAILABLE,
    STALE,
    INCOMPATIBLE,
    UNAUTHORIZED,
    BLOCKED,
    REMOVED
}

enum class LocalServiceState {
    REGISTERED,
    AVAILABLE,
    PAUSED,
    UNAVAILABLE,
    UNREGISTERED
}

enum class CancellationState {
    REQUESTED,
    DELIVERED,
    ACCEPTED,
    REJECTED,
    TOO_LATE,
    COMPLETED
}

enum class CancellationOutcome {
    CANCELLED_BEFORE_EXECUTION,
    CANCELLATION_REQUESTED_DURING_EXECUTION,
    HANDLER_CANCELLED,
    HANDLER_COMPLETED_BEFORE_CANCEL,
    TOO_LATE_TO_CANCEL,
    CANCELLATION_UNSUPPORTED
}

enum class RpcErrorCode {
    SERVICE_NOT_FOUND,
    SERVICE_INSTANCE_MISMATCH,
    SERVICE_UNAVAILABLE,
    SERVICE_PAUSED,
    CONTRACT_NOT_SUPPORTED,
    CONTRACT_DEFINITION_MISMATCH,
    METHOD_NOT_FOUND,
    METHOD_NOT_SUPPORTED,
    AUTHORIZATION_DENIED,
    REQUEST_SCHEMA_INVALID,
    RESPONSE_SCHEMA_INVALID,
    REQUEST_TOO_LARGE,
    RESPONSE_TOO_LARGE,
    REQUEST_OBJECT_MISSING,
    RESULT_OBJECT_MISSING,
    INVOCATION_EXPIRED,
    DEADLINE_EXCEEDED,
    DUPLICATE_INVOCATION_CONFLICT,
    IDEMPOTENCY_KEY_REQUIRED,
    IDEMPOTENCY_KEY_CONFLICT,
    EXECUTION_IN_PROGRESS,
    CANCELLATION_UNSUPPORTED,
    TOO_LATE_TO_CANCEL,
    UNKNOWN_OUTCOME,
    INTERNAL_RUNTIME_ERROR,
    SECURITY_STATE_UNAVAILABLE
}
