package spike.servicediscovery.storage

import spike.identity.domain.NodeId
import spike.servicediscovery.domain.*

/**
 * Pure persistence contract interface for Service Discovery & RPC Runtime per SPEC-011 Section 116.
 * Free of any primitive parameter obsession; uses strong domain types exclusively.
 */
interface RpcRuntimeRepository {
    suspend fun saveServiceDefinition(definition: ServiceDefinition)
    suspend fun getServiceDefinition(serviceId: ServiceId): ServiceDefinition?
    suspend fun listServiceDefinitions(): List<ServiceDefinition>

    suspend fun saveContract(contract: ServiceContract)
    suspend fun getContract(contractId: ContractId, version: ContractVersion): ServiceContract?

    suspend fun saveLocalRegistration(registration: LocalServiceRegistration)
    suspend fun getLocalRegistration(serviceId: ServiceId): LocalServiceRegistration?
    suspend fun listLocalRegistrations(): List<LocalServiceRegistration>

    suspend fun saveRemoteServiceRecord(record: RemoteServiceRecord)
    suspend fun getRemoteServiceRecord(providerNodeId: NodeId, serviceId: ServiceId): RemoteServiceRecord?
    suspend fun findRemoteServiceRecords(
        providerNodeId: NodeId? = null,
        serviceId: ServiceId? = null,
        contractId: ContractId? = null,
        availabilityState: ServiceAvailabilityState? = null
    ): List<RemoteServiceRecord>

    suspend fun saveNegotiatedContract(contract: NegotiatedContract)
    suspend fun getNegotiatedContract(providerNodeId: NodeId, serviceId: ServiceId): NegotiatedContract?

    suspend fun saveInvocation(invocation: RpcInvocation)
    suspend fun getInvocation(invocationId: InvocationId): RpcInvocation?
    suspend fun findPendingInvocations(): List<RpcInvocation>

    suspend fun saveExecution(execution: RpcExecution)
    suspend fun getExecution(executionId: ExecutionId): RpcExecution?
    suspend fun findExecutionByInvocation(callerNodeId: NodeId, invocationId: InvocationId): RpcExecution?
    suspend fun findPendingExecutions(): List<RpcExecution>

    suspend fun saveResult(result: RpcResult)
    suspend fun getResult(resultId: String): RpcResult?
    suspend fun getResultByInvocation(invocationId: InvocationId): RpcResult?

    suspend fun saveIdempotencyRecord(record: IdempotencyRecord)
    suspend fun getIdempotencyRecord(
        callerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        idempotencyKey: IdempotencyKey
    ): IdempotencyRecord?

    suspend fun saveCancellation(cancellation: RpcCancellation)
    suspend fun getCancellation(invocationId: InvocationId): RpcCancellation?

    suspend fun <T> transaction(block: suspend () -> T): T
}
