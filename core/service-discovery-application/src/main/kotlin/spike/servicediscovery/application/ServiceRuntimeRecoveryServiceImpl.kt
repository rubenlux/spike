package spike.servicediscovery.application

import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository

/**
 * Service Runtime Recovery Service per SPEC-011 Section 111.
 * Recovery Invariant: RECOVERY MUST NEVER BLINDLY RE-EXECUTE NON_IDEMPOTENT_COMMAND.
 */
class ServiceRuntimeRecoveryServiceImpl(
    private val repository: RpcRuntimeRepository
) {
    suspend fun recover() {
        val now = System.currentTimeMillis()

        // 1. Recover pending invocations
        val pendingInvocations = repository.findPendingInvocations()
        for (invocation in pendingInvocations) {
            val deadlineAt = invocation.deadlineAt
            if (deadlineAt != null && now > deadlineAt.epochMillis) {
                repository.saveInvocation(invocation.copy(state = RpcInvocationState.EXPIRED))
            }
        }

        // 2. Recover pending executions
        val pendingExecutions = repository.findPendingExecutions()
        for (execution in pendingExecutions) {
            if (execution.state == RpcExecutionState.RUNNING || execution.state == RpcExecutionState.COMMITTING_RESULT) {
                if (execution.methodSemantics == RpcMethodSemantics.NON_IDEMPOTENT_COMMAND) {
                    // Strict recovery invariant: Non-idempotent command interrupted during execution
                    // MUST be marked UNKNOWN_OUTCOME and NEVER blindly re-executed.
                    val unknownExecution = execution.copy(
                        state = RpcExecutionState.UNKNOWN_OUTCOME,
                        completedAt = now,
                        failureCode = RpcErrorCode.UNKNOWN_OUTCOME
                    )
                    repository.saveExecution(unknownExecution)

                    val unknownResult = RpcResult(
                        resultId = "res_${execution.invocationId.value}",
                        invocationId = execution.invocationId,
                        executionId = execution.executionId,
                        status = RpcResultStatus.UNKNOWN_OUTCOME,
                        errorDescriptor = "Execution interrupted by system recovery (NON_IDEMPOTENT_COMMAND)",
                        createdAt = now,
                        durableAt = now
                    )
                    repository.saveResult(unknownResult)

                    repository.getInvocation(execution.invocationId)?.let { inv ->
                        repository.saveInvocation(inv.copy(state = RpcInvocationState.UNKNOWN_OUTCOME, resultId = unknownResult.resultId))
                    }
                }
            }
        }
    }
}
