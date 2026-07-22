package spike.servicediscovery.application

import spike.identity.domain.NodeId
import spike.security.contracts.LocalPeerSecurityCore
import spike.security.domain.*
import spike.servicediscovery.contracts.RpcExecutionService
import spike.servicediscovery.contracts.RpcHandlerContext
import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository

class RpcExecutionServiceImpl(
    private val localNodeId: NodeId,
    private val repository: RpcRuntimeRepository,
    private val localServiceRegistry: LocalServiceRegistryImpl,
    private val localPeerSecurityCore: LocalPeerSecurityCore
) : RpcExecutionService {

    override suspend fun processInboundRequest(requestInvocation: RpcInvocation): RpcResult {
        val now = System.currentTimeMillis()

        // 1. Durable Deduplication Lookup based on persisted repository state
        val existingExecution = repository.findExecutionByInvocation(requestInvocation.callerNodeId, requestInvocation.invocationId)
        if (existingExecution != null) {
            when (existingExecution.state) {
                RpcExecutionState.COMPLETED, RpcExecutionState.RESULT_DURABLE -> {
                    val result = repository.getResultByInvocation(requestInvocation.invocationId)
                    if (result != null) return result
                }
                RpcExecutionState.RUNNING, RpcExecutionState.COMMITTING_RESULT -> {
                    throw ServiceRuntimeError.ExecutionInProgress(requestInvocation.invocationId)
                }
                RpcExecutionState.REJECTED -> {
                    val result = repository.getResultByInvocation(requestInvocation.invocationId)
                    if (result != null) return result
                    throw ServiceRuntimeError.AuthorizationDenied(requestInvocation.serviceId, requestInvocation.methodId, "Previous invocation rejected")
                }
                RpcExecutionState.UNKNOWN_OUTCOME -> {
                    val result = repository.getResultByInvocation(requestInvocation.invocationId)
                    if (result != null) return result
                    throw ServiceRuntimeError.UnknownOutcome(requestInvocation.invocationId)
                }
                else -> { /* Proceed with re-processing */ }
            }
        }

        // 2. Idempotency Check
        val idempotencyKey: IdempotencyKey? = requestInvocation.idempotencyKey
        if (idempotencyKey != null) {
            val idempotencyRecord = repository.getIdempotencyRecord(
                requestInvocation.callerNodeId,
                requestInvocation.serviceId,
                requestInvocation.methodId,
                idempotencyKey
            )
            if (idempotencyRecord != null) {
                if (idempotencyRecord.requestHash != requestInvocation.requestPayloadDescriptor.payloadHash) {
                    throw ServiceRuntimeError.IdempotencyKeyConflict(idempotencyKey)
                }
                val cachedResultId: String? = idempotencyRecord.resultId
                if (cachedResultId != null) {
                    val cachedResult = repository.getResult(cachedResultId)
                    if (cachedResult != null) return cachedResult
                }
            }
        }

        // 3. Service & Method Lookup
        val definition = repository.getServiceDefinition(requestInvocation.serviceId)
            ?: throw ServiceRuntimeError.ServiceNotFound(requestInvocation.serviceId)
        val method = definition.methods.find { it.methodId == requestInvocation.methodId }
            ?: throw ServiceRuntimeError.MethodNotFound(requestInvocation.methodId)

        val handler = localServiceRegistry.getServiceHandler(requestInvocation.serviceId)
            ?: throw ServiceRuntimeError.ServiceUnavailable(requestInvocation.serviceId)

        // 4. SPEC-009 Authorization Gateway Verification (MUST OCCUR BEFORE ANY HANDLER EXECUTION OR SIDE EFFECT)
        val authRequest = AuthorizationRequest(
            peerNodeId = requestInvocation.callerNodeId,
            capabilityId = CapabilityId(method.requiredCapabilityId),
            operationType = OperationType("RPC_INVOCATION"),
            resourceType = ResourceType("SERVICE"),
            resourceId = requestInvocation.serviceId.value,
            requestedUsage = 1L,
            context = AuthorizationContext(),
            requestedAt = now
        )

        val decision = localPeerSecurityCore.authorize(authRequest)
        if (decision.effect != AuthorizationEffect.ALLOW) {
            // Persist rejected execution and result, NO HANDLER EXECUTION OCCURS
            val rejectedExecution = RpcExecution(
                invocationId = requestInvocation.invocationId,
                callerNodeId = requestInvocation.callerNodeId,
                providerNodeId = localNodeId,
                serviceId = requestInvocation.serviceId,
                serviceInstanceId = requestInvocation.serviceInstanceId,
                methodId = requestInvocation.methodId,
                methodSemantics = requestInvocation.methodSemantics,
                idempotencyKey = requestInvocation.idempotencyKey,
                state = RpcExecutionState.REJECTED,
                authorizationDecisionId = decision.decisionId.value,
                requestReceivedAt = now,
                completedAt = now,
                failureCode = RpcErrorCode.AUTHORIZATION_DENIED
            )
            repository.saveExecution(rejectedExecution)

            val rejectedResult = RpcResult(
                resultId = "res_${requestInvocation.invocationId.value}",
                invocationId = requestInvocation.invocationId,
                executionId = rejectedExecution.executionId,
                status = RpcResultStatus.AUTHORIZATION_DENIED,
                errorDescriptor = "Authorization denied by SPEC-009: ${decision.reasonCode.name}",
                createdAt = now,
                durableAt = now
            )
            repository.saveResult(rejectedResult)

            throw ServiceRuntimeError.AuthorizationDenied(
                requestInvocation.serviceId,
                requestInvocation.methodId,
                decision.reasonCode.name
            )
        }

        // 5. Durable Execution Record Creation in state ACCEPTED before handler execution
        val execution = RpcExecution(
            invocationId = requestInvocation.invocationId,
            callerNodeId = requestInvocation.callerNodeId,
            providerNodeId = localNodeId,
            serviceId = requestInvocation.serviceId,
            serviceInstanceId = requestInvocation.serviceInstanceId,
            methodId = requestInvocation.methodId,
            methodSemantics = requestInvocation.methodSemantics,
            idempotencyKey = requestInvocation.idempotencyKey,
            state = RpcExecutionState.ACCEPTED,
            authorizationDecisionId = decision.decisionId.value,
            requestReceivedAt = now,
            acceptedAt = now
        )
        repository.saveExecution(execution)

        // 6. Execute Application Handler
        val runningExecution = execution.copy(state = RpcExecutionState.RUNNING, startedAt = System.currentTimeMillis())
        repository.saveExecution(runningExecution)

        val handlerContext = RpcHandlerContext(
            invocationId = requestInvocation.invocationId,
            executionId = execution.executionId,
            callerNodeId = requestInvocation.callerNodeId,
            serviceId = requestInvocation.serviceId,
            serviceInstanceId = requestInvocation.serviceInstanceId,
            contractVersion = requestInvocation.contractVersion,
            methodId = requestInvocation.methodId,
            methodSemantics = requestInvocation.methodSemantics,
            authorizationDecisionId = decision.decisionId.value,
            deadline = requestInvocation.deadlineAt
        )

        val responsePayload = try {
            handler.handle(handlerContext, requestInvocation.requestPayloadDescriptor)
        } catch (ex: Exception) {
            val failedNow = System.currentTimeMillis()
            val failedExecution = runningExecution.copy(
                state = RpcExecutionState.FAILED,
                completedAt = failedNow,
                failureCode = RpcErrorCode.INTERNAL_RUNTIME_ERROR
            )
            repository.saveExecution(failedExecution)

            val failedResult = RpcResult(
                resultId = "res_${requestInvocation.invocationId.value}",
                invocationId = requestInvocation.invocationId,
                executionId = execution.executionId,
                status = RpcResultStatus.APPLICATION_ERROR,
                errorDescriptor = ex.message ?: "Handler execution failed",
                createdAt = failedNow,
                durableAt = failedNow
            )
            repository.saveResult(failedResult)
            return failedResult
        }

        // 7. Commit Result Durably
        val commitNow = System.currentTimeMillis()
        repository.saveExecution(runningExecution.copy(state = RpcExecutionState.COMMITTING_RESULT))

        val result = RpcResult(
            resultId = "res_${requestInvocation.invocationId.value}",
            invocationId = requestInvocation.invocationId,
            executionId = execution.executionId,
            status = RpcResultStatus.SUCCESS,
            responsePayloadDescriptor = responsePayload,
            createdAt = commitNow,
            durableAt = commitNow
        )
        repository.saveResult(result)

        val currentKey: IdempotencyKey? = idempotencyKey
        if (currentKey != null) {
            val idempotencyRecord = IdempotencyRecord(
                callerNodeId = requestInvocation.callerNodeId,
                serviceId = requestInvocation.serviceId,
                methodId = requestInvocation.methodId,
                idempotencyKey = currentKey,
                invocationId = requestInvocation.invocationId,
                executionId = execution.executionId,
                requestHash = requestInvocation.requestPayloadDescriptor.payloadHash,
                state = RpcExecutionState.COMPLETED,
                resultId = result.resultId,
                createdAt = commitNow,
                expiresAt = commitNow + 86_400_000L // 24 hours retention
            )
            repository.saveIdempotencyRecord(idempotencyRecord)
        }

        val completedExecution = runningExecution.copy(
            state = RpcExecutionState.COMPLETED,
            completedAt = commitNow,
            resultId = result.resultId
        )
        repository.saveExecution(completedExecution)

        return result
    }

    override suspend fun getExecution(executionId: ExecutionId): RpcExecution? {
        return repository.getExecution(executionId)
    }
}
