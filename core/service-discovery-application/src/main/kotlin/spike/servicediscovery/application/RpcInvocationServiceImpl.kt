package spike.servicediscovery.application

import spike.identity.domain.NodeId
import spike.servicediscovery.contracts.ContractNegotiationService
import spike.servicediscovery.contracts.RpcExecutionService
import spike.servicediscovery.contracts.RpcInvocationService
import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository

class RpcInvocationServiceImpl(
    private val callerNodeId: NodeId,
    private val repository: RpcRuntimeRepository,
    private val contractNegotiationService: ContractNegotiationService,
    private val rpcExecutionService: RpcExecutionService
) : RpcInvocationService {

    override suspend fun createInvocation(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey?,
        deadline: Deadline?
    ): RpcInvocation {
        val negotiated = contractNegotiationService.negotiateContract(
            providerNodeId = providerNodeId,
            serviceId = serviceId,
            clientRequiredVersion = ContractVersion("1.0")
        )

        val now = System.currentTimeMillis()

        // InvocationId MUST be immutable across retries, reconnects, transport switches, and recovery
        val invocation = RpcInvocation(
            invocationId = InvocationId.generate(),
            callerNodeId = callerNodeId,
            providerNodeId = providerNodeId,
            serviceId = serviceId,
            serviceInstanceId = negotiated.serviceInstanceId,
            contractId = negotiated.contractId,
            contractVersion = negotiated.selectedVersion,
            methodId = methodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            idempotencyKey = idempotencyKey,
            requestPayloadDescriptor = requestPayload,
            state = RpcInvocationState.PERSISTED,
            createdAt = now,
            deadlineAt = deadline,
            expiresAt = deadline?.epochMillis ?: (now + 86_400_000L)
        )

        repository.saveInvocation(invocation)
        return invocation
    }

    override suspend fun invoke(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey?,
        deadline: Deadline?
    ): RpcResult {
        val invocation = createInvocation(providerNodeId, serviceId, methodId, requestPayload, idempotencyKey, deadline)

        // Deadline check before sending
        if (deadline != null && System.currentTimeMillis() > deadline.epochMillis) {
            val expiredInvocation = invocation.copy(state = RpcInvocationState.EXPIRED)
            repository.saveInvocation(expiredInvocation)
            throw ServiceRuntimeError.DeadlineExceeded(invocation.invocationId)
        }

        val sendingInvocation = invocation.copy(state = RpcInvocationState.SENDING, attemptCount = invocation.attemptCount + 1, lastAttemptAt = System.currentTimeMillis())
        repository.saveInvocation(sendingInvocation)

        val result = rpcExecutionService.processInboundRequest(sendingInvocation)

        val completedState = when (result.status) {
            RpcResultStatus.SUCCESS -> RpcInvocationState.COMPLETED
            RpcResultStatus.AUTHORIZATION_DENIED -> RpcInvocationState.REJECTED
            RpcResultStatus.UNKNOWN_OUTCOME -> RpcInvocationState.UNKNOWN_OUTCOME
            else -> RpcInvocationState.FAILED
        }

        repository.saveInvocation(sendingInvocation.copy(state = completedState, resultId = result.resultId))
        return result
    }

    override suspend fun getInvocation(invocationId: InvocationId): RpcInvocation? {
        return repository.getInvocation(invocationId)
    }

    override suspend fun getResult(invocationId: InvocationId): RpcResult? {
        return repository.getResultByInvocation(invocationId)
    }

    override suspend fun cancelInvocation(invocationId: InvocationId, reason: CancellationReason): RpcCancellation {
        val invocation = repository.getInvocation(invocationId)
            ?: throw ServiceRuntimeError.UnknownOutcome(invocationId)

        val now = System.currentTimeMillis()
        val cancellation = RpcCancellation(
            invocationId = invocationId,
            requestedByNodeId = callerNodeId,
            reason = reason,
            requestedAt = now,
            state = CancellationState.REQUESTED
        )

        repository.saveCancellation(cancellation)
        repository.saveInvocation(invocation.copy(state = RpcInvocationState.CANCELLATION_REQUESTED, cancellationState = CancellationState.REQUESTED))
        return cancellation
    }
}
