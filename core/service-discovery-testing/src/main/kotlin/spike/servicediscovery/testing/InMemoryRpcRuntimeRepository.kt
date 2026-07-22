package spike.servicediscovery.testing

import spike.identity.domain.NodeId
import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory RpcRuntimeRepository for SPEC-011 acceptance tests.
 * Provides direct map access for assertion purposes.
 */
class InMemoryRpcRuntimeRepository : RpcRuntimeRepository {
    val serviceDefinitions   = ConcurrentHashMap<ServiceId, ServiceDefinition>()
    val contracts            = ConcurrentHashMap<String, ServiceContract>()
    val localRegistrations   = ConcurrentHashMap<ServiceId, LocalServiceRegistration>()
    val remoteRecords        = ConcurrentHashMap<String, RemoteServiceRecord>()
    val negotiatedContracts  = ConcurrentHashMap<String, NegotiatedContract>()
    val invocations          = ConcurrentHashMap<InvocationId, RpcInvocation>()
    val executions           = ConcurrentHashMap<ExecutionId, RpcExecution>()
    val results              = ConcurrentHashMap<String, RpcResult>()
    val idempotencyRecords   = ConcurrentHashMap<String, IdempotencyRecord>()
    val cancellations        = ConcurrentHashMap<InvocationId, RpcCancellation>()

    override suspend fun saveServiceDefinition(definition: ServiceDefinition) {
        serviceDefinitions[definition.serviceId] = definition
    }
    override suspend fun getServiceDefinition(serviceId: ServiceId): ServiceDefinition? = serviceDefinitions[serviceId]
    override suspend fun listServiceDefinitions(): List<ServiceDefinition> = serviceDefinitions.values.toList()

    override suspend fun saveContract(contract: ServiceContract) {
        contracts["${contract.contractId.value}:${contract.contractVersion.value}"] = contract
    }
    override suspend fun getContract(contractId: ContractId, version: ContractVersion): ServiceContract? =
        contracts["${contractId.value}:${version.value}"]

    override suspend fun saveLocalRegistration(registration: LocalServiceRegistration) {
        localRegistrations[registration.serviceId] = registration
    }
    override suspend fun getLocalRegistration(serviceId: ServiceId): LocalServiceRegistration? = localRegistrations[serviceId]
    override suspend fun listLocalRegistrations(): List<LocalServiceRegistration> = localRegistrations.values.toList()

    override suspend fun saveRemoteServiceRecord(record: RemoteServiceRecord) {
        remoteRecords["${record.providerNodeId.value}:${record.serviceId.value}"] = record
    }
    override suspend fun getRemoteServiceRecord(providerNodeId: NodeId, serviceId: ServiceId): RemoteServiceRecord? =
        remoteRecords["${providerNodeId.value}:${serviceId.value}"]
    override suspend fun findRemoteServiceRecords(
        providerNodeId: NodeId?,
        serviceId: ServiceId?,
        contractId: ContractId?,
        availabilityState: ServiceAvailabilityState?
    ): List<RemoteServiceRecord> = remoteRecords.values.filter { r ->
        (providerNodeId == null || r.providerNodeId == providerNodeId) &&
        (serviceId == null || r.serviceId == serviceId) &&
        (contractId == null || r.contractId == contractId) &&
        (availabilityState == null || r.availabilityState == availabilityState)
    }

    override suspend fun saveNegotiatedContract(contract: NegotiatedContract) {
        negotiatedContracts["${contract.providerNodeId.value}:${contract.serviceId.value}"] = contract
    }
    override suspend fun getNegotiatedContract(providerNodeId: NodeId, serviceId: ServiceId): NegotiatedContract? =
        negotiatedContracts["${providerNodeId.value}:${serviceId.value}"]

    override suspend fun saveInvocation(invocation: RpcInvocation) {
        invocations[invocation.invocationId] = invocation
    }
    override suspend fun getInvocation(invocationId: InvocationId): RpcInvocation? = invocations[invocationId]
    override suspend fun findPendingInvocations(): List<RpcInvocation> = invocations.values.filter {
        it.state !in setOf(
            RpcInvocationState.COMPLETED, RpcInvocationState.REJECTED,
            RpcInvocationState.FAILED, RpcInvocationState.EXPIRED, RpcInvocationState.CANCELLED
        )
    }

    override suspend fun saveExecution(execution: RpcExecution) {
        executions[execution.executionId] = execution
    }
    override suspend fun getExecution(executionId: ExecutionId): RpcExecution? = executions[executionId]
    override suspend fun findExecutionByInvocation(callerNodeId: NodeId, invocationId: InvocationId): RpcExecution? =
        executions.values.find { it.callerNodeId == callerNodeId && it.invocationId == invocationId }
    override suspend fun findPendingExecutions(): List<RpcExecution> = executions.values.filter {
        it.state !in setOf(
            RpcExecutionState.COMPLETED, RpcExecutionState.REJECTED,
            RpcExecutionState.FAILED, RpcExecutionState.EXPIRED, RpcExecutionState.CANCELLED
        )
    }

    override suspend fun saveResult(result: RpcResult) { results[result.resultId] = result }
    override suspend fun getResult(resultId: String): RpcResult? = results[resultId]
    override suspend fun getResultByInvocation(invocationId: InvocationId): RpcResult? =
        results.values.find { it.invocationId == invocationId }

    override suspend fun saveIdempotencyRecord(record: IdempotencyRecord) {
        idempotencyRecords["${record.callerNodeId.value}:${record.serviceId.value}:${record.methodId.value}:${record.idempotencyKey.value}"] = record
    }
    override suspend fun getIdempotencyRecord(
        callerNodeId: NodeId, serviceId: ServiceId, methodId: MethodId, idempotencyKey: IdempotencyKey
    ): IdempotencyRecord? =
        idempotencyRecords["${callerNodeId.value}:${serviceId.value}:${methodId.value}:${idempotencyKey.value}"]

    override suspend fun saveCancellation(cancellation: RpcCancellation) {
        cancellations[cancellation.invocationId] = cancellation
    }
    override suspend fun getCancellation(invocationId: InvocationId): RpcCancellation? = cancellations[invocationId]

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}
