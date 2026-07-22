package spike.servicediscovery.domain

sealed class ServiceRuntimeError(
    val errorCode: RpcErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class ServiceNotFound(val serviceId: ServiceId) :
        ServiceRuntimeError(RpcErrorCode.SERVICE_NOT_FOUND, "Service not found: ${serviceId.value}")

    class ServiceInstanceMismatch(val expected: ServiceInstanceId, val actual: ServiceInstanceId) :
        ServiceRuntimeError(RpcErrorCode.SERVICE_INSTANCE_MISMATCH, "Service instance mismatch: expected ${expected.value}, actual ${actual.value}")

    class ServiceUnavailable(val serviceId: ServiceId) :
        ServiceRuntimeError(RpcErrorCode.SERVICE_UNAVAILABLE, "Service unavailable: ${serviceId.value}")

    class ServicePaused(val serviceId: ServiceId) :
        ServiceRuntimeError(RpcErrorCode.SERVICE_PAUSED, "Service paused: ${serviceId.value}")

    class ContractNotSupported(val contractId: ContractId, val version: ContractVersion) :
        ServiceRuntimeError(RpcErrorCode.CONTRACT_NOT_SUPPORTED, "Contract ${contractId.value} version ${version.value} not supported")

    class ContractDefinitionMismatch(val contractId: ContractId, val localHash: ContractHash, val remoteHash: ContractHash) :
        ServiceRuntimeError(RpcErrorCode.CONTRACT_DEFINITION_MISMATCH, "Contract ${contractId.value} definition hash mismatch: local=${localHash.value}, remote=${remoteHash.value}")

    class MethodNotFound(val methodId: MethodId) :
        ServiceRuntimeError(RpcErrorCode.METHOD_NOT_FOUND, "Method not found: ${methodId.value}")

    class AuthorizationDenied(val serviceId: ServiceId, val methodId: MethodId, val reason: String) :
        ServiceRuntimeError(RpcErrorCode.AUTHORIZATION_DENIED, "Authorization denied for ${serviceId.value}/${methodId.value}: $reason")

    class DeadlineExceeded(val invocationId: InvocationId) :
        ServiceRuntimeError(RpcErrorCode.DEADLINE_EXCEEDED, "Deadline exceeded for invocation: ${invocationId.value}")

    class DuplicateInvocationConflict(val invocationId: InvocationId) :
        ServiceRuntimeError(RpcErrorCode.DUPLICATE_INVOCATION_CONFLICT, "Duplicate invocation conflict: ${invocationId.value}")

    class IdempotencyKeyConflict(val key: IdempotencyKey) :
        ServiceRuntimeError(RpcErrorCode.IDEMPOTENCY_KEY_CONFLICT, "Idempotency key conflict: ${key.value}")

    class ExecutionInProgress(val invocationId: InvocationId) :
        ServiceRuntimeError(RpcErrorCode.EXECUTION_IN_PROGRESS, "Execution in progress: ${invocationId.value}")

    class CancellationUnsupported(val invocationId: InvocationId) :
        ServiceRuntimeError(RpcErrorCode.CANCELLATION_UNSUPPORTED, "Cancellation unsupported for invocation: ${invocationId.value}")

    class TooLateToCancel(val invocationId: InvocationId) :
        ServiceRuntimeError(RpcErrorCode.TOO_LATE_TO_CANCEL, "Too late to cancel invocation: ${invocationId.value}")

    class UnknownOutcome(val invocationId: InvocationId) :
        ServiceRuntimeError(RpcErrorCode.UNKNOWN_OUTCOME, "Unknown outcome for invocation: ${invocationId.value}")

    class InternalRuntimeError(detail: String, cause: Throwable? = null) :
        ServiceRuntimeError(RpcErrorCode.INTERNAL_RUNTIME_ERROR, detail, cause)
}
